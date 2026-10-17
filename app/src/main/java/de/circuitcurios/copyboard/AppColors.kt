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
            background = Color.rgb(18, 19, 22),
            surface = Color.rgb(28, 30, 35),
            inputSurface = Color.rgb(32, 34, 40),
            textPrimary = Color.rgb(241, 241, 238),
            textSecondary = Color.rgb(164, 167, 173),
            border = Color.rgb(58, 61, 68),
            accent = Color.rgb(143, 169, 159)
        )
    } else {
        AppColors(
            background = Color.rgb(246, 246, 243),
            surface = Color.WHITE,
            inputSurface = Color.rgb(250, 250, 248),
            textPrimary = Color.rgb(31, 33, 36),
            textSecondary = Color.rgb(102, 106, 111),
            border = Color.rgb(220, 222, 218),
            accent = Color.rgb(65, 91, 82)
        )
    }
}
