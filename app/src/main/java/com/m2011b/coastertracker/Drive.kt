package com.m2011b.coastertracker

import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.m2011b.coastertracker.data.Backup
import com.m2011b.coastertracker.data.Repo
import com.m2011b.kit.theme.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google Drive backup. One file in Drive's hidden app folder (drive.appdata scope): other apps and the
 * Drive file list can't see it, and this app can't see anything else in Drive.
 */
class DriveBackup(private val context: Context, private val repo: Repo, private val theme: ThemeStore, prefsName: String = "drive") {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val lock = Mutex()

    /** The signed-in Google account, or null when signed out. */
    var email by mutableStateOf(prefs.getString("email", null))
        internal set
    var auto by mutableStateOf(prefs.getBoolean("auto", true))
        private set
    /** When this phone last finished a backup (ms), 0 if never. */
    var backupAt by mutableStateOf(prefs.getLong("backup_at", 0L))
        internal set
    var busy by mutableStateOf(false)
        private set

    val needsBackup get() = email != null && repo.changedAt > backupAt

    /** A backup found in Drive, waiting for the user to restore it or not. */
    data class Found(val rides: Int, val savedAt: Long, val json: String)

    fun updateAuto(on: Boolean) { auto = on; prefs.edit().putBoolean("auto", on).apply() }

    private fun request(account: String?) = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .apply { if (account != null) setAccount(Account(account, "com.google")) }
        .build()

    /** Step one of signing in. If the result has a resolution, launch its PendingIntent and pass the reply to [finishSignIn]. */
    suspend fun startSignIn(): AuthorizationResult = Identity.getAuthorizationClient(context).authorize(request(null)).await()

    fun resultFrom(data: Intent?): AuthorizationResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    /** Records the account, then returns the backup already in Drive if there is one; otherwise backs up right away. */
    suspend fun finishSignIn(r: AuthorizationResult): Found? = work {
        val token = r.accessToken ?: throw IllegalStateException("Google didn't grant Drive access.")
        val who = r.toGoogleSignInAccount()?.email ?: Api(token).email()
        email = who; prefs.edit().putString("email", who).apply()
        val api = Api(token)
        val f = api.find()
        if (f != null) api.download(f).let { json -> Backup.peek(json).let { (n, at) -> Found(n, at, json) } }
        else { upload(api); null }
    }

    /** Uploads this phone's data now. */
    suspend fun backUpNow() = work { upload(api()) }

    /** Reads the backup in Drive without changing anything, for the restore prompt. */
    suspend fun fetch(): Found? = work {
        val api = api(); api.find()?.let { id -> api.download(id).let { json -> Backup.peek(json).let { (n, at) -> Found(n, at, json) } } }
    }

    /** Merges [f] into this phone and applies its colors. Returns rides added. */
    fun restore(f: Found): Int {
        val r = repo.restoreBackup(f.json)
        r.theme?.let { theme.importCode(it) }
        return r.added
    }

    /** Called when the app goes to the background: backs up quietly if anything changed. Never shows a prompt. */
    fun autoBackup() {
        if (!auto || !needsBackup) return
        background.launch { runCatching { backUpNow() } }
    }

    suspend fun signOut() = work {
        runCatching {
            val r = Identity.getAuthorizationClient(context).authorize(request(email)).await()
            r.accessToken?.let { t ->
                // Revoke the grant so the next sign-in asks which account again.
                post("https://oauth2.googleapis.com/revoke?token=" + URLEncoder.encode(t, "UTF-8"))
                runCatching { GoogleAuthUtil.clearToken(context, t) }
            }
        }
        email = null; backupAt = 0
        prefs.edit().remove("email").remove("backup_at").apply()
    }

    private suspend fun <T> work(block: suspend () -> T): T = lock.withLock {
        withContext(Dispatchers.Main) { busy = true }
        try { withContext(Dispatchers.IO) { block() } } finally { withContext(Dispatchers.Main) { busy = false } }
    }

    /** A token without any prompt; fails if Google wants the user to sign in again. */
    private suspend fun api(): Api {
        val who = email ?: throw IllegalStateException("Not signed in.")
        val r = Identity.getAuthorizationClient(context).authorize(request(who)).await()
        if (r.hasResolution() || r.accessToken == null) throw IllegalStateException("Google needs you to sign in again.")
        return Api(r.accessToken!!)
    }

    private fun upload(api: Api) {
        val started = System.currentTimeMillis()
        val json = repo.exportBackup(theme.exportCode())
        val id = api.find()
        if (id == null) api.create(json) else api.update(id, json)
        backupAt = started; prefs.edit().putLong("backup_at", started).apply()
    }

    private fun post(url: String) = (URL(url).openConnection() as HttpURLConnection).run {
        requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        outputStream.close(); responseCode.also { disconnect() }
    }

    /** Drive REST v3, just the calls the backup needs. */
    private inner class Api(private val token: String) {
        private fun call(method: String, url: String, type: String? = null, body: ByteArray? = null): String {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("Authorization", "Bearer $token")
            if (method == "PATCH") { c.requestMethod = "POST"; c.setRequestProperty("X-HTTP-Method-Override", "PATCH") } else c.requestMethod = method
            if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", type); c.outputStream.use { it.write(body) } }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            if (code == 401) runCatching { GoogleAuthUtil.clearToken(context, token) }
            if (code !in 200..299) throw IllegalStateException(
                runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull() ?: "Google Drive error $code")
            return text
        }

        fun email(): String = JSONObject(call("GET", "$API/about?fields=user")).getJSONObject("user").getString("emailAddress")

        fun find(): String? {
            val q = URLEncoder.encode("name='$FILE'", "UTF-8")
            val files = JSONObject(call("GET", "$API/files?spaces=appDataFolder&q=$q&fields=files(id,modifiedTime)&orderBy=modifiedTime desc".replace(" ", "%20")))
                .getJSONArray("files")
            return if (files.length() == 0) null else files.getJSONObject(0).getString("id")
        }

        fun download(id: String) = call("GET", "$API/files/$id?alt=media")

        fun create(json: String) {
            val b = "liftHillBoundary"
            val meta = JSONObject().put("name", FILE).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
            val body = "--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$b\r\nContent-Type: application/json\r\n\r\n$json\r\n--$b--\r\n"
            call("POST", "$UPLOAD/files?uploadType=multipart", "multipart/related; boundary=$b", body.toByteArray())
        }

        fun update(id: String, json: String) = call("PATCH", "$UPLOAD/files/$id?uploadType=media", "application/json", json.toByteArray())
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val FILE = "lift-hill-backup.json"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
