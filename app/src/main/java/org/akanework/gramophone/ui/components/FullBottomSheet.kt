package org.akanework.gramophone.ui.components

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.player.FullPlayerScreen

class FullBottomSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val activity
        get() = context as MainActivity

    var minimize: (() -> Unit)? = null

    val expansionFractionState = androidx.compose.runtime.mutableFloatStateOf(0f)
    var expansionFraction: Float
        get() = expansionFractionState.floatValue
        set(value) {
            expansionFractionState.floatValue = value
        }

    // Сохраняем для обратной совместимости с PlayerBottomSheet
    val bottomSheetFullLyricView: ComposeView = ComposeView(context).apply {
        visibility = GONE
    }

    companion object {
        const val SLIDER_UPDATE_INTERVAL: Long = 100
        const val BACKGROUND_COLOR_TRANSITION_SEC: Long = 300
        const val FOREGROUND_COLOR_TRANSITION_SEC: Long = 150
        const val LYRIC_FADE_TRANSITION_SEC: Long = 125
        private const val TAG = "FullBottomSheet"
    }

    init {
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val darkTheme = isSystemInDarkTheme()
                val hasDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

                val colorScheme = when {
                    hasDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
                    hasDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
                    darkTheme -> darkColorScheme()
                    else -> lightColorScheme()
                }

                MaterialTheme(colorScheme = colorScheme) {
                    val fraction = expansionFractionState.floatValue
                    FullPlayerScreen(
                        onDismiss = { minimize?.invoke() },
                        fragmentManager = activity.supportFragmentManager,
                        expansionFraction = fraction
                    )
                }
            }
        }
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(bottomSheetFullLyricView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun onStart() {}
    fun onStop() {}
}
