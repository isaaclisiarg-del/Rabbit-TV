package com.luncher.launcher.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** System options nested below Rabbit TV's Configuration panel. */
class SystemSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        findViewById<TextView>(R.id.settings_title).setText(R.string.settings_system)
        val entries = findViewById<LinearLayout>(R.id.settings_entries)
        addEntry(entries, R.string.settings_home_screen) {
            startActivity(Intent(this, HomeOverrideSettingsActivity::class.java))
        }
        addEntry(entries, R.string.settings_device_settings) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (exception: ActivityNotFoundException) {
                Toast.makeText(this, R.string.settings_system_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
        val scroll = findViewById<View>(R.id.settings_entries_scroll)
        scroll.layoutParams.height = 0
        scroll.layoutParams.height = listHeight(findViewById(R.id.settings_panel), entries.childCount)
        entries.getChildAt(0)?.requestFocus()
    }

    private fun addEntry(parent: LinearLayout, label: Int, onClick: () -> Unit) {
        val view = layoutInflater.inflate(R.layout.settings_entry, parent, false) as TextView
        view.setText(label)
        view.setOnClickListener { onClick() }
        parent.addView(view)
    }
}
