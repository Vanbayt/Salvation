/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.fragments.settings

import android.os.Bundle
import androidx.preference.Preference
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.fragments.BasePreferenceFragment
import org.akanework.gramophone.ui.fragments.BaseSettingsActivity

class MainSettingsActivity : BaseSettingsActivity(
    R.string.home_menu_settings,
    { MainSettingsFragment() })

class MainSettingsFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_top, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "appearance" -> {
                startActivity(AppearanceSettingsActivity::class.java)
            }

            "behavior" -> {
                startActivity(BehaviorSettingsActivity::class.java)
            }

            "about" -> {
                startActivity(AboutSettingsActivity::class.java)
            }

            "player" -> {
                startActivity(PlayerSettingsActivity::class.java)
            }

            "audio" -> {
                startActivity(AudioSettingsActivity::class.java)
            }

            "experimental" -> {
                startActivity(ExperimentalSettingsActivity::class.java)
            }

            "diagnostics" -> {
                showDiagnosticsDialog()
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun showDiagnosticsDialog() {
        val ctx = context ?: return
        org.akanework.gramophone.logic.utils.PlaybackLogger.init(ctx)
        val logs = org.akanework.gramophone.logic.utils.PlaybackLogger.getLogs()

        val scrollView = android.widget.ScrollView(ctx)
        val textView = android.widget.TextView(ctx).apply {
            text = logs
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(32, 24, 32, 24)
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("Логи воспроизведения")
            .setView(scrollView)
            .setPositiveButton("Скопировать") { _, _ ->
                val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Salvation Logs", logs)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(ctx, "Лог скопирован в буфер обмена", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Очистить") { _, _ ->
                org.akanework.gramophone.logic.utils.PlaybackLogger.clearLogs()
                android.widget.Toast.makeText(ctx, "Логи очищены", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

}
