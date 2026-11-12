package com.m2011b.coastertracker

import android.net.Uri
import com.m2011b.coastertracker.data.Milestone
import java.text.Normalizer
import java.util.Locale

/**
 * Share links for completion cards. The site (web/ in this repo, on Cloudflare) shows a pre-drawn
 * preview card for the park, model or manufacturer, and the person's own card when the link is opened.
 * The link carries only what's on the card.
 */
object ShareLinks {
    /** Where the share site lives. Changes to a custom domain later. */
    var base = "https://lift-hill.lifthill.workers.dev"

    /** Same as slug() in web/src/card.js, which names the pre-drawn images. */
    fun slug(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKD).replace(Regex("[\\u0300-\\u036f]"), "")
        .lowercase(Locale.ROOT).replace("&", " and ").replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "x" }

    fun link(m: Milestone): String {
        val b = Uri.parse(base).buildUpon()
        when (m) {
            is Milestone.ParkDone -> b.appendPath("p").appendPath("${m.park.id}")
                .appendQueryParameter("n", m.park.name).appendQueryParameter("c", "${m.coasters}")
                .appendQueryParameter("m", "${m.models}").appendQueryParameter("y", "${m.year}")
            is Milestone.ModelDone -> b.appendPath("m").appendPath("${slug(m.make)}--${slug(m.model)}")
                .appendQueryParameter("w", if (m.world) "1" else "0").appendQueryParameter("mk", m.make)
                .appendQueryParameter("md", m.model).appendQueryParameter("sm", shortMake(m.make))
                .appendQueryParameter("r", "${m.count}").appendQueryParameter("d", "${m.date}")
            is Milestone.MakeDone -> b.appendPath("k").appendPath(slug(m.make))
                .appendQueryParameter("w", if (m.world) "1" else "0").appendQueryParameter("mk", m.make)
                .appendQueryParameter("c", "${m.coasters}").appendQueryParameter("m", "${m.models}")
                .appendQueryParameter("p", "${m.places}").appendQueryParameter("d", "${m.date}")
        }
        return b.appendQueryParameter("no", "${m.creditNo}").build().toString()
    }

    /** "Cedar Point: 100% Complete", "B&M Wing Coaster (US): 100% Complete". Also the preview's title. */
    fun title(m: Milestone): String = when (m) {
        is Milestone.ParkDone -> m.park.name
        is Milestone.ModelDone -> "${shortMake(m.make)} ${m.model} (${if (m.world) "Worldwide" else "US"})"
        is Milestone.MakeDone -> "${m.make} (${if (m.world) "Worldwide" else "US"})"
    } + ": 100% Complete"

    /** One message: a short line and the link, whose preview shows the card and the full title. */
    fun message(m: Milestone) = "100% Complete ${link(m)}"
}
