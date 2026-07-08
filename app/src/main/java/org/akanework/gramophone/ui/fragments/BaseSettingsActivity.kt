package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.ui.BaseActivity

abstract class BaseSettingsActivity(
    private val str: Int,
    private val fragmentCreator: () -> Fragment
) : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.fragment_top_settings)
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val collapsingToolbar = findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)

        findViewById<AppBarLayout>(R.id.appbarlayout).enableEdgeToEdgePaddingListener()
        collapsingToolbar.title = getString(str)

        topAppBar.setNavigationOnClickListener {
            finish()
        }

        supportFragmentManager
            .beginTransaction()
            .add(R.id.settings, fragmentCreator())
            .commit()
    }
}