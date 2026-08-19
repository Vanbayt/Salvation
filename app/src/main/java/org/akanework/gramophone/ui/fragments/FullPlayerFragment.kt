package org.akanework.gramophone.ui.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.player.FullPlayerScreen

class FullPlayerFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = requireContext()
                val darkTheme = isSystemInDarkTheme()
                val hasDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

                val colorScheme = when {
                    hasDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
                    hasDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
                    darkTheme -> darkColorScheme()
                    else -> lightColorScheme()
                }

                MaterialTheme(colorScheme = colorScheme) {
                    FullPlayerScreen(
                        onDismiss = { dismiss() },
                        fragmentManager = parentFragmentManager
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? FrameLayout

        bottomSheet?.let { sheet ->
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            sheet.requestLayout()
        }
    }
}