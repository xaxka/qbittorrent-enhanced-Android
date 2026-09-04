package io.github.xixka.qbittorrent.util

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors

/**
 * Theme handling: Material You dynamic colors (on by default where the
 * platform supports them) and the day/night mode, both driven by the
 * appearance settings — matching the behavior of LibreTorrent's
 * AppearanceSettings (theme + palette).
 */
object ThemeUtils {

    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    /** True when this device can use wallpaper-based dynamic colors (12L+). */
    val supportsDynamicColors: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Applies the saved day/night mode app-wide. Must be called before
     * activities are created (QBApp.onCreate) and again whenever the user
     * changes the setting; AppCompatDelegate recreates open activities.
     */
    fun applyThemeMode(mode: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /**
     * Applies Material You dynamic colors to a single activity when enabled.
     * Call in onCreate() before inflating content. On devices below Android
     * 12 this is a no-op and the app keeps its static Material 3 palette.
     */
    fun applyDynamicColors(activity: androidx.appcompat.app.AppCompatActivity, enabled: Boolean) {
        if (enabled && supportsDynamicColors) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }
}
