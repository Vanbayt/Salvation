package org.akanework.gramophone.ui.fragments.settings

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.ui.BaseActivity
import org.akanework.gramophone.ui.adapters.BlacklistFolderAdapter

class BlacklistSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.fragment_blacklist_settings)
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        findViewById<AppBarLayout>(R.id.appbarlayout).enableEdgeToEdgePaddingListener()

        topAppBar.setNavigationOnClickListener {
            finish()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
        recyclerView.enableEdgeToEdgePaddingListener()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = BlacklistFolderAdapter(this, prefs)
    }
}