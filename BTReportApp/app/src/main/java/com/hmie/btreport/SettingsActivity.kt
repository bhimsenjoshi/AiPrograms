package com.hmie.btreport

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hmie.btreport.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Load saved values
        val prefs = getPrefs(this)
        b.etApiKey.setText(prefs.getString(KEY_API_KEY, ""))
        b.etDefaultEmployee.setText(prefs.getString(KEY_EMPLOYEE_NAME, ""))
        b.etDefaultEmployeeId.setText(prefs.getString(KEY_EMPLOYEE_ID, ""))
        b.etDefaultDept.setText(prefs.getString(KEY_DEPARTMENT, ""))
        b.etDefaultDesignation.setText(prefs.getString(KEY_DESIGNATION, ""))
        b.etDefaultCostCenter.setText(prefs.getString(KEY_COST_CENTER, ""))

        b.btnSave.setOnClickListener {
            val key = b.etApiKey.text.toString().trim()
            if (key.isBlank()) {
                b.etApiKey.error = "API key required"
                return@setOnClickListener
            }
            prefs.edit()
                .putString(KEY_API_KEY, key)
                .putString(KEY_EMPLOYEE_NAME, b.etDefaultEmployee.text.toString().trim())
                .putString(KEY_EMPLOYEE_ID, b.etDefaultEmployeeId.text.toString().trim())
                .putString(KEY_DEPARTMENT, b.etDefaultDept.text.toString().trim())
                .putString(KEY_DESIGNATION, b.etDefaultDesignation.text.toString().trim())
                .putString(KEY_COST_CENTER, b.etDefaultCostCenter.text.toString().trim())
                .apply()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    companion object {
        const val KEY_API_KEY = "claude_api_key"
        const val KEY_EMPLOYEE_NAME = "employee_name"
        const val KEY_EMPLOYEE_ID = "employee_id"
        const val KEY_DEPARTMENT = "department"
        const val KEY_DESIGNATION = "designation"
        const val KEY_COST_CENTER = "cost_center"

        fun getPrefs(context: Context) =
            context.getSharedPreferences("bt_report_prefs", Context.MODE_PRIVATE)

        fun getApiKey(context: Context): String =
            getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }
}
