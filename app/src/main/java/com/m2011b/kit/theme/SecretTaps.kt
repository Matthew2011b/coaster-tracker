package com.m2011b.kit.theme

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Makes an area unlock something after [count] quick taps, each within [windowMs] of the last.
 * No ripple or other feedback, so the area looks empty. Used for the hidden theme menu:
 * put it on the blank space at the bottom of the side menu.
 */
fun Modifier.secretTaps(count: Int = 5, windowMs: Long = 1500, onUnlock: () -> Unit): Modifier = composed {
    val taps = remember { mutableIntStateOf(0) }
    val last = remember { mutableLongStateOf(0L) }
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        val now = SystemClock.elapsedRealtime()
        taps.intValue = if (now - last.longValue <= windowMs) taps.intValue + 1 else 1
        last.longValue = now
        if (taps.intValue >= count) { taps.intValue = 0; onUnlock() }
    }
}
