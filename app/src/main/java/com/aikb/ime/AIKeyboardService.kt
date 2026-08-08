package com.aikb.ime

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.aikb.ime.ai.AIClient
import com.aikb.ime.ai.AISkill
import com.aikb.ime.ai.skills.BusinessSkills
import com.aikb.ime.ai.skills.LoveSkills
import com.aikb.ime.ui.CandidateStrip
import com.aikb.ime.ui.SettingsActivity
import com.aikb.ime.util.Preferences

class AIKeyboardService : android.inputmethodservice.InputMethodService() {

    companion object {
        private val BG_ROOT = 0xFF22C55E.toInt()
        private val BG_KEY = 0xFF1E3A5F.toInt()
        private val BG_CAPS_ON = 0xFF2563EB.toInt()
        private val BG_SEND = 0xFF16A34A.toInt()
        private val C_WHITE = 0xFFFFFFFF.toInt()
        private val C_DEL = 0xFFF87171.toInt()
        private val C_SPACE = 0xFF94A3B8.toInt()
        private val C_SET = 0xFFFBBF24.toInt()
        private val C_FALLBACK_TEXT = 0xFF000000.toInt()
    }

    private var candidateStrip: CandidateStrip? = null
    private var inputView: View? = null
    private var capsButton: Button? = null
    private var isCaps = false
    private var emojiIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private var aiRunnable: Runnable? = null
    private val DEBOUNCE_MS = 500L

    @Volatile private var currentRequestId = 0

    private val letterKeys = listOf("q","w","e","r","t","y","u","i","o","p",
        "a","s","d","f","g","h","j","k","l",
        "z","x","c","v","b","n","m")

    override fun onCreate() {
        super.onCreate()
        try {
            Preferences.init(this)
        } catch (e: Exception) {
            Log.e("AIKeyboard", "onCreate: Preferences.init 失败", e)
        }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
    }

    override fun onCreateInputView(): View {
        return try {
            Log.d("AIKeyboard", "onCreateInputView: building keyboard in code")

            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(BG_ROOT)
                setPadding(0, 0, 0, dp2px(4))
            }

            val strip = CandidateStrip(this)
            root.addView(strip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp2px(36), 0f
            ).apply { topMargin = dp2px(4); leftMargin = dp2px(4); rightMargin = dp2px(4) })
            candidateStrip = strip
            candidateStrip?.setSuggestionsCallback { pos ->
                candidateStrip?.getSuggestion(pos)?.let { commitText(it) }
            }

            val keyboardContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            root.addView(keyboardContainer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            keyboardContainer.addView(buildRow(44, 0, listOf(
                k("Q"), k("W"), k("E"), k("R"), k("T"), k("Y"),
                k("U"), k("I"), k("O"), k("P")
            )))

            keyboardContainer.addView(buildRow(44, dp2px(4), listOf(
                k("A"), k("S"), k("D"), k("F"), k("G"), k("H"),
                k("J"), k("K"), k("L")
            )))

            val row3 = buildRow(44, dp2px(4), listOf(
                k("CAPS", 2f, 12f),
                k("Z"), k("X"), k("C"), k("V"), k("B"),
                k("N"), k("M"),
                k("DEL", 2f, 14f, C_DEL)
            ))
            keyboardContainer.addView(row3)
            capsButton = row3.getChildAt(0) as? Button

            keyboardContainer.addView(buildRow(44, 0, listOf(
                k("SPACE", 1.5f, 12f, C_SPACE),
                k("SEND", 1f, 12f, C_WHITE, BG_SEND),
                k("Aa", 1f, 14f),
                k("SET", 1f, 14f, C_SET)
            )))

            attachListeners(keyboardContainer)
            inputView = root
            root
        } catch (e: Exception) {
            Log.e("AIKeyboard", "onCreateInputView error", e)
            createFallbackView(e)
        }
    }

    data class KeyDef(
        val label: String,
        val weight: Float = 1f,
        val textSize: Float = 16f,
        val textColor: Int = C_WHITE,
        val bgColor: Int = BG_KEY
    )

    private fun k(
        label: String,
        weight: Float = 1f,
        size: Float = 16f,
        color: Int = C_WHITE,
        bg: Int = BG_KEY
    ): KeyDef = KeyDef(label, weight, size, color, bg)

    private fun buildRow(rowHeightDp: Int, hPadding: Int, keys: List<KeyDef>): LinearLayout {
        val rowHeight = dp2px(rowHeightDp)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(hPadding, 0, hPadding, 0)
        }
        keys.forEach { kd ->
            val button = Button(this).apply {
                text = kd.label
                textSize = kd.textSize
                setTextColor(kd.textColor)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                minimumHeight = 0
                minimumWidth = 0
                setBackgroundColor(kd.bgColor)
            }
            val params = LinearLayout.LayoutParams(0, dp2px(rowHeightDp), kd.weight)
            params.setMargins(1, 1, 1, 1)
            row.addView(button, params)
        }
        return row
    }

    private fun attachListeners(container: LinearLayout) {
        for (c in 0 until container.childCount) {
            val row = container.getChildAt(c) as LinearLayout
            for (i in 0 until row.childCount) {
                val button = row.getChildAt(i) as Button
                val label = button.text.toString()
                button.setOnClickListener {
                    when (label) {
                        "CAPS" -> toggleCaps()
                        "DEL" -> { deleteLastChar() }
                        "SPACE" -> { commitText(" "); scheduleAISuggest() }
                        "SEND" -> sendAction()
                        "Aa" -> insertEmoji()
                        "SET" -> startActivity(Intent(this, SettingsActivity::class.java))
                        else -> {
                            val lower = label.lowercase()
                            if (lower.length == 1 && lower in letterKeys) {
                                val text = if (isCaps) lower.uppercase() else lower
                                commitText(text)
                                scheduleAISuggest()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun dp2px(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()

    private fun createFallbackView(e: Exception): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_ROOT)
            minimumHeight = 220
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layout.addView(TextView(this).apply {
            text = "键盘加载失败\n错误: ${e.javaClass.simpleName}: ${e.message}"
            textSize = 16f
            setTextColor(C_WHITE)
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 8)
        }, params)
        val stackLines = e.stackTrace.take(10).joinToString("\n") { it.toString() }
        layout.addView(TextView(this).apply {
            text = stackLines
            textSize = 10f
            setTextColor(C_FALLBACK_TEXT)
            gravity = Gravity.START
            setPadding(24, 0, 24, 16)
            maxLines = 15
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        return layout
    }

    private fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(SpannableString(text), 1)
    }

    private fun toggleCaps() {
        isCaps = !isCaps
        val btn = capsButton ?: return
        btn.setBackgroundColor(if (isCaps) BG_CAPS_ON else BG_KEY)
        btn.setTextColor(C_WHITE)
    }

    private fun deleteLastChar() {
        currentInputConnection?.deleteSurroundingText(1, 0)
        scheduleAISuggest()
    }

    private fun sendAction() {
        currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_SEND)
    }

    private fun insertEmoji() {
        val emojis = listOf("😊", "😂", "❤️", "🥰", "😘", "😭", "🤗", "😎", "🔥", "💕", "👍", "✨")
        emojiIndex = (emojiIndex + 1) % emojis.size
        commitText(emojis[emojiIndex])
    }

    private fun scheduleAISuggest() {
        aiRunnable?.let { handler.removeCallbacks(it) }
        aiRunnable = Runnable { fetchAISuggestions() }
        handler.postDelayed(aiRunnable!!, DEBOUNCE_MS)
    }

    private fun fetchAISuggestions() {
        try {
            val skill = findSkill(Preferences.activeSkill) ?: return
            val ic = currentInputConnection ?: return
            val text = ic.getTextBeforeCursor(80, 0)?.toString() ?: return
            if (text.isBlank()) return

            val requestId = ++currentRequestId
            val prompt = skill.suggestionPrompt(text, emptyList())
            AIClient.generate(skill.systemPrompt(), prompt) { result ->
                handler.post {
                    try {
                        if (requestId == currentRequestId) {
                            parseSuggestions(result)?.let {
                                candidateStrip?.setSuggestions(it)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AIKeyboard", "parseSuggestions 异常", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AIKeyboard", "fetchAISuggestions 异常", e)
        }
    }

    private fun parseSuggestions(raw: String): List<String>? {
        if (raw.startsWith("[") || raw.contains("网络错误") || raw.contains("API错误") ||
            raw.contains("模型不存在") || raw.contains("请先在设置")) {
            return null
        }
        val lines = raw.split("\n")
            .map { it.trim().removePrefix("-").removePrefix("•").removePrefix("·").trim() }
            .filter { it.isNotBlank() && it.length <= 50 }
            .take(5)
        return if (lines.isNotEmpty()) lines else null
    }

    private fun findSkill(id: String): AISkill? {
        val all = LoveSkills.all().map { it as AISkill } + BusinessSkills.all().map { it as AISkill }
        return all.find { it.id == id } ?: all.firstOrNull()
    }
}