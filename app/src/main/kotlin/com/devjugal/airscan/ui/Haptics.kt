package com.devjugal.airscan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

enum class HapticKind { Better, Worse, Confirm, Off }

/** The tap for a change of signal level, or null when nothing changed or there is no earlier level. */
fun hapticFor(
    old: Quality?,
    new: Quality,
): HapticKind? =
    when {
        old == null || old == new -> null
        new > old -> HapticKind.Better
        else -> HapticKind.Worse
    }

/** Whether the user allows haptic feedback. The Access tab holds the switch. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

class Haptics(
    private val feedback: HapticFeedback,
    private val enabled: Boolean,
) {
    fun perform(kind: HapticKind) {
        if (!enabled) return
        feedback.performHapticFeedback(
            when (kind) {
                HapticKind.Better -> HapticFeedbackType.SegmentTick
                HapticKind.Worse -> HapticFeedbackType.SegmentFrequentTick
                HapticKind.Confirm -> HapticFeedbackType.Confirm
                HapticKind.Off -> HapticFeedbackType.ToggleOff
            },
        )
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { Haptics(feedback, enabled) }
}
