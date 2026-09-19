package pt.rjp.aikeyboard.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*
import pt.rjp.aikeyboard.core.DictionaryUpdateManager
import pt.rjp.aikeyboard.core.KeyboardPreferences
import pt.rjp.aikeyboard.core.Language
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var prefs: KeyboardPreferences
    private lateinit var updateStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = KeyboardPreferences(this)
        DictionaryUpdateManager.schedule(this)
        DictionaryUpdateManager.maybeUpdateNow(this)
        setContentView(buildUi())
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "RJP AI Keyboard"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF0B2948.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Corretor rápido, dicionários locais, atualização automática e aprendizagem pessoal. Sem emojis."
            textSize = 16f
            setPadding(0, dp(8), 0, dp(18))
        })

        root.addView(button("1. Ativar teclado") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(button("2. Selecionar RJP AI Keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        root.addView(section("Idioma"))
        val spinner = Spinner(this)
        val languages = Language.entries
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages.map { it.displayName })
        spinner.setSelection(languages.indexOf(prefs.language))
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { prefs.language = languages[position] }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        root.addView(spinner)

        root.addView(section("Correção"))
        root.addView(toggle("Autocorreção", prefs.autoCorrect) { prefs.autoCorrect = it })
        root.addView(toggle("Contexto da palavra anterior", prefs.smartContext) { prefs.smartContext = it })
        root.addView(toggle("Maiúscula automática", prefs.autoCapitalize) { prefs.autoCapitalize = it })
        root.addView(toggle("Vibração das teclas", prefs.vibration) { prefs.vibration = it })
        root.addView(toggle("Modo privado (não aprende palavras)", prefs.privateMode) { prefs.privateMode = it })

        root.addView(section("Dicionários"))
        root.addView(toggle("Atualizar dicionários automaticamente", DictionaryUpdateManager.isAutoUpdateEnabled(this)) {
            DictionaryUpdateManager.setAutoUpdateEnabled(this, it)
            refreshUpdateStatus()
        })
        root.addView(button("Atualizar agora") {
            updateStatus.text = "A verificar atualizações..."
            DictionaryUpdateManager.updateNowAsync(this) { result ->
                runOnUiThread {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    refreshUpdateStatus()
                }
            }
        })
        updateStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(5), 0, 0)
        }
        root.addView(updateStatus)
        refreshUpdateStatus()

        root.addView(section("IA opcional"))
        root.addView(toggle("Ativar IA remota apenas no botão AI", prefs.aiEnabled) { prefs.aiEnabled = it })
        val aiEndpoint = EditText(this).apply {
            hint = "https://seu-backend.exemplo/correct"
            setText(prefs.aiEndpoint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) prefs.aiEndpoint = text.toString() }
        }
        root.addView(aiEndpoint)
        root.addView(TextView(this).apply {
            text = "A autocorreção normal continua local e instantânea. O endpoint é usado apenas quando toca em AI; nenhuma chave secreta é incluída no APK."
            textSize = 13f
            setPadding(0, dp(5), 0, 0)
        })

        root.addView(section("Idiomas incluídos"))
        root.addView(TextView(this).apply {
            text = "Português (Portugal) · Inglês · Italiano · Espanhol · Francês · Alemão\n\nA app mantém uma cópia local válida de cada dicionário. As novas versões são verificadas em segundo plano e só substituem a versão anterior depois de validação de formato e SHA-256."
            textSize = 15f
        })
        return scroll
    }

    private fun refreshUpdateStatus() {
        if (!::updateStatus.isInitialized) return
        val url = DictionaryUpdateManager.configuredManifestUrl()
        if (url.isBlank()) {
            updateStatus.text = "Feed automático ainda não configurado neste APK. Compile pelo workflow do GitHub incluído para o URL ser configurado automaticamente."
            return
        }
        val success = DictionaryUpdateManager.lastSuccess(this)
        val error = DictionaryUpdateManager.lastError(this)
        val whenText = if (success > 0L) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(success)) else "ainda sem atualização"
        updateStatus.text = if (error.isBlank()) "Última atualização válida: $whenText" else "Última atualização válida: $whenText\nÚltimo erro: $error"
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text
        textSize = 19f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFF0B2948.toInt())
        setPadding(0, dp(22), 0, dp(8))
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
        gravity = Gravity.CENTER
    }

    private fun toggle(text: String, checked: Boolean, action: (Boolean) -> Unit) = Switch(this).apply {
        this.text = text
        isChecked = checked
        setPadding(0, dp(6), 0, dp(6))
        setOnCheckedChangeListener { _, value -> action(value) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
