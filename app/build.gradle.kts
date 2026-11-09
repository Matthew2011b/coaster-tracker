import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keyProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Trimmed: a secret pasted with a trailing newline or space otherwise fails as a wrong alias/password.
fun keyValue(env: String, prop: String): String? =
    (System.getenv(env)?.takeIf { it.isNotBlank() } ?: keyProps.getProperty(prop))?.trim()

android {
    namespace = "com.m2011b.coastertracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.m2011b.coastertracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    // Signing. The key is NEVER committed. It comes from (first match wins):
    //   1. Environment variables, used by the GitHub Actions build:
    //      APP_KEYSTORE (path), APP_KEYSTORE_PASSWORD, APP_KEY_ALIAS, APP_KEY_PASSWORD
    //   2. A local keystore.properties file (gitignored) with storeFile, storePassword, keyAlias, keyPassword
    // With neither, release builds come out unsigned and debug builds use the machine's debug key,
    // which will NOT install over the copy already on the phone.
    val storePath = keyValue("APP_KEYSTORE", "storeFile")
    val appSigning = if (storePath != null) signingConfigs.create("release") {
        storeFile = rootProject.file(storePath)
        storePassword = keyValue("APP_KEYSTORE_PASSWORD", "storePassword")
        keyAlias = keyValue("APP_KEY_ALIAS", "keyAlias")
        keyPassword = keyValue("APP_KEY_PASSWORD", "keyPassword")
    } else null

    buildTypes {
        release {
            isMinifyEnabled = false
            if (appSigning != null) signingConfig = appSigning
        }
        debug {
            if (appSigning != null) signingConfig = appSigning
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.16.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.46.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test>().configureEach {
    // Test outputs (screenshots) go under app/build/test-out.
    val out = layout.buildDirectory.dir("test-out").get().asFile
    systemProperty("shotsDir", File(out, "shots").path)
    doFirst { out.mkdirs() }
    systemProperty("roborazzi.test.record", "true")
    testLogging { showStandardStreams = true; events("passed", "failed") }
}
