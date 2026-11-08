package org.akanework.gramophone.logic.ui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.theme.AppThemeHelper

open class BaseActivity : AppCompatActivity() {
    lateinit var prefs: SharedPreferences
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "pureDark" || key == "theme_mode") {
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (AppThemeHelper.isDarkTheme(this) && AppThemeHelper.isPureDark(this)) {
            setTheme(R.style.Theme_Gramophone_PureDark)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onDestroy()
    }
}
