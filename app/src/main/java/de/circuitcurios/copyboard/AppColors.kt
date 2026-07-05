package de.circuitcurios.copyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

data class AppColors(
    val background: Int,
    val surface: Int,
    val inputSurface: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val border: Int,
    val accent: Int
)

fun resolveAppColors(context: Context): AppColors {
    val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val dark = mode == Configuration.UI_MODE_NIGHT_YES

    return if (dark) {
        AppColors(
            background = Color.rgb(18, 18, 18),
            surface = Color.rgb(31, 31, 31),
            inputSurface = Color.rgb(26, 26, 26),
            textPrimary = Color.rgb(245, 245, 245),
            textSecondary = Color.rgb(170, 170, 170),
            border = Color.rgb(70, 70, 70),
            accent = Color.rgb(230, 230, 230)
        )
    } else {
        AppColors(
            background = Color.rgb(250, 250, 250),
            surface = Color.WHITE,
            inputSurface = Color.WHITE,
            textPrimary = Color.rgb(25, 25, 25),
            textSecondary = Color.rgb(90, 90, 90),
            border = Color.rgb(226, 226, 226),
            accent = Color.rgb(34, 34, 34)
        )
    }
}
