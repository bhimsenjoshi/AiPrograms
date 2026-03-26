package com.hmie.btreport

import android.content.Context
import android.os.Bundle
import android.view.View
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

        val prefs = getPrefs(this)

        // Restore provider selection
        val saved = prefs.getString(KEY_AI_PROVIDER, AiReceiptService.Provider.GROQ.name)
        when (saved) {
            AiReceiptService.Provider.GROQ.name    -> b.radioGroq.isChecked = true
            AiReceiptService.Provider.GEMINI.name  -> b.radioGemini.isChecked = true
            AiReceiptService.Provider.OLLAMA.name  -> b.radioOllama.isChecked = true
            AiReceiptService.Provider.CLAUDE.name  -> b.radioClaude.isChecked = true
            else -> b.radioGroq.isChecked = true
        }
        refreshPanels()

        b.radioGroq.setOnClickListener   { refreshPanels() }
        b.radioGemini.setOnClickListener { refreshPanels() }
        b.radioOllama.setOnClickListener { refreshPanels() }
        b.radioClaude.setOnClickListener { refreshPanels() }

        // Restore saved values
        b.etApiKey.setText(prefs.getString(KEY_API_KEY, ""))
        b.etOllamaEndpoint.setText(prefs.getString(KEY_OLLAMA_ENDPOINT, "http://192.168.1.x:11434"))
        b.etOllamaModel.setText(prefs.getString(KEY_OLLAMA_MODEL, "llava:latest"))
        b.etDefaultEmployee.setText(prefs.getString(KEY_EMPLOYEE_NAME, ""))
        b.etDefaultEmployeeId.setText(prefs.getString(KEY_EMPLOYEE_ID, ""))
        b.etDefaultDept.setText(prefs.getString(KEY_DEPARTMENT, ""))
        b.etDefaultDesignation.setText(prefs.getString(KEY_DESIGNATION, ""))
        b.etDefaultCostCenter.setText(prefs.getString(KEY_COST_CENTER, ""))

        b.btnSave.setOnClickListener {
            val provider = selectedProvider()

            // Validate required fields
            if (provider != AiReceiptService.Provider.OLLAMA) {
                if (b.etApiKey.text.toString().isBlank()) {
                    b.etApiKey.error = "API key required"
                    return@setOnClickListener
                }
            } else {
                if (b.etOllamaEndpoint.text.toString().isBlank()) {
                    b.etOllamaEndpoint.error = "URL required"
                    return@setOnClickListener
                }
            }

            prefs.edit()
                .putString(KEY_AI_PROVIDER, provider.name)
                .putString(KEY_API_KEY, b.etApiKey.text.toString().trim())
                .putString(KEY_OLLAMA_ENDPOINT, b.etOllamaEndpoint.text.toString().trim())
                .putString(KEY_OLLAMA_MODEL, b.etOllamaModel.text.toString().trim().ifBlank { "llava:latest" })
                .putString(KEY_EMPLOYEE_NAME, b.etDefaultEmployee.text.toString().trim())
                .putString(KEY_EMPLOYEE_ID, b.etDefaultEmployeeId.text.toString().trim())
                .putString(KEY_DEPARTMENT, b.etDefaultDept.text.toString().trim())
                .putString(KEY_DESIGNATION, b.etDefaultDesignation.text.toString().trim())
                .putString(KEY_COST_CENTER, b.etDefaultCostCenter.text.toString().trim())
                .apply()

            Toast.makeText(this, "Settings saved ✓", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun selectedProvider(): AiReceiptService.Provider = when {
        b.radioGroq.isChecked   -> AiReceiptService.Provider.GROQ
        b.radioGemini.isChecked -> AiReceiptService.Provider.GEMINI
        b.radioOllama.isChecked -> AiReceiptService.Provider.OLLAMA
        b.radioClaude.isChecked -> AiReceiptService.Provider.CLAUDE
        else -> AiReceiptService.Provider.GROQ
    }

    private fun refreshPanels() {
        val isOllama = b.radioOllama.isChecked
        b.layoutOllama.visibility = if (isOllama) View.VISIBLE else View.GONE
        b.layoutClaude.visibility = if (!isOllama) View.VISIBLE else View.GONE

        // Update hint text based on selected provider
        b.tvApiKeyHint.text = when {
            b.radioGroq.isChecked   -> "Groq API Key – get free at console.groq.com"
            b.radioGemini.isChecked -> "Gemini API Key – get free at aistudio.google.com"
            else                    -> "Claude API Key – get at console.anthropic.com"
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    companion object {
        const val KEY_AI_PROVIDER    = "ai_provider"
        const val KEY_API_KEY        = "api_key"
        const val KEY_OLLAMA_ENDPOINT = "ollama_endpoint"
        const val KEY_OLLAMA_MODEL   = "ollama_model"
        const val KEY_EMPLOYEE_NAME  = "employee_name"
        const val KEY_EMPLOYEE_ID    = "employee_id"
        const val KEY_DEPARTMENT     = "department"
        const val KEY_DESIGNATION    = "designation"
        const val KEY_COST_CENTER    = "cost_center"

        fun getPrefs(context: Context) =
            context.getSharedPreferences("bt_report_prefs", Context.MODE_PRIVATE)

        fun getApiKey(context: Context): String =
            getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }
}
