package org.akanework.gramophone.ui.fragments.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.BaseFragment

class StatsFragment : BaseFragment(false) {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner))
            setContent {
                val context = requireContext()
                val isDarkTheme = isSystemInDarkTheme()
                val dynamicColorScheme = when {
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                        if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    }
                    isDarkTheme -> darkColorScheme()
                    else -> lightColorScheme()
                }

                MaterialTheme(colorScheme = dynamicColorScheme) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        StatsScreen(
                            onBackPressed = {
                                (activity as? MainActivity)?.supportFragmentManager?.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun newInstance(): StatsFragment {
            return StatsFragment()
        }
    }
}
