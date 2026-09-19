package pt.rjp.aikeyboard.ime

import android.inputmethodservice.InputMethodService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import pt.rjp.aikeyboard.core.*
import java.util.Locale
import java.util.concurrent.Executors

class RjpKeyboardService : InputMethodService() {
    private lateinit var prefs: KeyboardPreferences
    private lateinit var repository: DictionaryRepository
    private lateinit var engine: CorrectionEngine
    private val aiGateway = AiGateway()
    private lateinit var keyboard: RjpKeyboardView
    private lateinit var candidateViews: List<TextView>
    private lateinit var languageView: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val composing = StringBuilder()
    private var previousWord: String? = null
    private var shift = false
    private var symbols = false
    private var suggestionGeneration = 0
    private val dictionaryUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DictionaryUpdateManager.ACTION_DICTIONARIES_UPDATED) {
                repository.invalidateAll()
                executor.execute { repository.get(prefs.language) }
                if (::keyboard.isInitialized) updateSuggestions()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = KeyboardPreferences(this)
        repository = DictionaryRepository(this)
        engine = CorrectionEngine(repository)
        val filter = IntentFilter(DictionaryUpdateManager.ACTION_DICTIONARIES_UPDATED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(dictionaryUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dictionaryUpdateReceiver, filter)
        }
        DictionaryUpdateManager.schedule(this)
        DictionaryUpdateManager.maybeUpdateNow(this)
        executor.execute { repository.get(prefs.language) }
    }

    override fun onDestroy() {
        try { unregisterReceiver(dictionaryUpdateReceiver) } catch (_: Exception) {}
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF1F3F6.toInt())
        }

        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val ai = toolbarButton("AI") { smartFixCurrentWord() }
        languageView = toolbarButton(prefs.language.shortLabel) { cycleLanguage() }
        val settings = toolbarButton("DEF") {
            Toast.makeText(this, "Abra RJP AI Keyboard para definições", Toast.LENGTH_SHORT).show()
        }
        toolbar.addView(ai, LinearLayout.LayoutParams(0, dp(38), 1f))
        toolbar.addView(languageView, LinearLayout.LayoutParams(0, dp(38), 1f))
        toolbar.addView(settings, LinearLayout.LayoutParams(0, dp(38), 1f))
        root.addView(toolbar)

        val candidates = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        candidateViews = (0..2).map { index ->
            TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 16f
                setTextColor(0xFF17202A.toInt())
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { acceptSuggestion(text.toString()) }
                candidates.addView(this, LinearLayout.LayoutParams(0, dp(42), 1f))
            }
        }
        root.addView(candidates)

        keyboard = RjpKeyboardView(this).apply { onKey = ::handleKey }
        refreshLayout()
        root.addView(keyboard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composing.clear()
        previousWord = null
        shift = prefs.autoCapitalize && shouldAutoCapitalize(attribute)
        symbols = false
        if (::keyboard.isInitialized) refreshLayout()
        clearSuggestions()
    }

    private fun handleKey(key: KeySpec) {
        vibrate()
        when (key.value) {
            KeyboardLayout.SHIFT -> { shift = !shift; refreshLayout() }
            KeyboardLayout.DELETE -> backspace()
            KeyboardLayout.SPACE -> commitWordAndSeparator(" ")
            KeyboardLayout.ENTER -> commitWordAndSeparator("\n")
            KeyboardLayout.SYMBOLS -> { symbols = true; refreshLayout() }
            KeyboardLayout.LETTERS -> { symbols = false; refreshLayout() }
            KeyboardLayout.LANGUAGE -> cycleLanguage()
            else -> typeText(key.value)
        }
    }

    private fun typeText(text: String) {
        if (text.length == 1 && (text[0].isLetter() || text[0] == '\'' || text[0] == '’')) {
            composing.append(text)
            currentInputConnection?.setComposingText(composing, 1)
            shift = false
            refreshLayout()
            updateSuggestions()
        } else {
            finishComposing(false)
            currentInputConnection?.commitText(text, 1)
            if (text in listOf(".", "!", "?")) shift = prefs.autoCapitalize
            refreshLayout()
        }
    }

    private fun commitWordAndSeparator(separator: String) {
        if (composing.isNotEmpty()) {
            val source = composing.toString()
            val correction = if (prefs.autoCorrect && !isSensitiveField()) engine.autoCorrection(prefs.language, source, previousWord) else null
            val finalWord = correction ?: source
            currentInputConnection?.commitText(finalWord, 1)
            if (!prefs.privateMode && !isSensitiveField()) engine.learn(prefs.language, finalWord)
            previousWord = finalWord
            composing.clear()
        } else {
            currentInputConnection?.finishComposingText()
        }
        currentInputConnection?.commitText(separator, 1)
        shift = separator == "\n" || (prefs.autoCapitalize && sentenceEndedBeforeCursor())
        refreshLayout()
        clearSuggestions()
    }

    private fun finishComposing(learn: Boolean) {
        if (composing.isNotEmpty()) {
            val word = composing.toString()
            currentInputConnection?.commitText(word, 1)
            if (learn && !prefs.privateMode && !isSensitiveField()) engine.learn(prefs.language, word)
            previousWord = word
            composing.clear()
        }
        currentInputConnection?.finishComposingText()
    }

    private fun backspace() {
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.lastIndex)
            currentInputConnection?.setComposingText(composing, 1)
            updateSuggestions()
        } else currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun updateSuggestions() {
        val word = composing.toString()
        if (word.isBlank() || isSensitiveField()) { clearSuggestions(); return }
        val generation = ++suggestionGeneration
        val language = prefs.language
        val prev = if (prefs.smartContext) previousWord else null
        executor.execute {
            val items = engine.suggestions(language, word, prev, 3)
            mainHandler.post {
                if (generation != suggestionGeneration || word != composing.toString()) return@post
                candidateViews.forEachIndexed { i, v -> v.text = items.getOrNull(i)?.word.orEmpty() }
            }
        }
    }

    private fun acceptSuggestion(word: String) {
        if (word.isBlank()) return
        currentInputConnection?.commitText(preserveTypedCase(composing.toString(), word), 1)
        previousWord = word
        composing.clear()
        clearSuggestions()
    }

    private fun smartFixCurrentWord() {
        if (isSensitiveField()) return
        val selected = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        val source = selected.ifBlank { composing.toString() }
        if (source.isBlank()) {
            Toast.makeText(this, "Selecione texto ou escreva uma palavra", Toast.LENGTH_SHORT).show()
            return
        }
        if (!prefs.aiEnabled || prefs.aiEndpoint.isBlank()) {
            updateSuggestions()
            Toast.makeText(this, "Correção inteligente local ativa", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "A corrigir texto selecionado…", Toast.LENGTH_SHORT).show()
        executor.execute {
            val result = aiGateway.correct(prefs.aiEndpoint, prefs.language, source)
            mainHandler.post {
                result.onSuccess { fixed ->
                    if (selected.isNotBlank()) currentInputConnection?.commitText(fixed, 1)
                    else { composing.clear(); composing.append(fixed); currentInputConnection?.setComposingText(composing, 1); updateSuggestions() }
                }.onFailure { Toast.makeText(this, "IA indisponível: ${it.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun cycleLanguage() {
        finishComposing(true)
        val values = Language.entries
        prefs.language = values[(values.indexOf(prefs.language) + 1) % values.size]
        languageView.text = prefs.language.shortLabel
        executor.execute { repository.get(prefs.language) }
        refreshLayout()
        clearSuggestions()
    }

    private fun refreshLayout() {
        if (!::keyboard.isInitialized) return
        keyboard.rows = if (symbols) KeyboardLayout.symbols(prefs.language) else KeyboardLayout.letters(prefs.language, shift)
        if (::languageView.isInitialized) languageView.text = prefs.language.shortLabel
    }

    private fun clearSuggestions() {
        suggestionGeneration++
        if (::candidateViews.isInitialized) candidateViews.forEach { it.text = "" }
    }

    private fun isSensitiveField(): Boolean {
        val t = currentInputEditorInfo?.inputType ?: return false
        val variation = t and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun shouldAutoCapitalize(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return true
        return inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 || inputType and InputType.TYPE_CLASS_TEXT != 0
    }

    private fun sentenceEndedBeforeCursor(): Boolean {
        val before = currentInputConnection?.getTextBeforeCursor(4, 0)?.toString()?.trimEnd().orEmpty()
        return before.endsWith('.') || before.endsWith('!') || before.endsWith('?')
    }

    private fun preserveTypedCase(source: String, word: String): String =
        if (source.firstOrNull()?.isUpperCase() == true) word.replaceFirstChar { it.uppercase(Locale.forLanguageTag(prefs.language.tag)) } else word

    private fun toolbarButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 15f
        setTextColor(0xFF0B2948.toInt())
        setOnClickListener { onClick() }
    }

    private fun vibrate() {
        if (!prefs.vibration) return
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(12, 35)) else @Suppress("DEPRECATION") vibrator.vibrate(12)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
