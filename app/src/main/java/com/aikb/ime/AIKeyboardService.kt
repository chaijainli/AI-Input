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
        Log.d("AIKeyboard", "onCreate: service starting")
        try {
            Preferences.init(this)
        } catch (e: Exception) {
            Log.e("AIKeyboard", "onCreate: Preferences.init 失败", e)
        }
    }

    override fun onStartInput(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        Log.d("AIKeyboard", "onStartInput: keyboard should appear")
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d("AIKeyboard", "onStartInputView: view should be visible")
    }

    override fun onCreateInputView(): View {
        return try {
            Log.d("AIKeyboard", "onCreateInputView: building keyboard in code")

            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF22C55E.toInt())
                setPadding(0, 0, 0, dp2px(4))
            }

            // Candidate strip
            val strip = CandidateStrip(this)
            root.addView(strip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp2px(36), 0f
            ).apply { topMargin = dp2px(4); leftMargin = dp2px(4); rightMargin = dp2px(4) })
            candidateStrip = strip
            candidateStrip?.setSuggestionsCallback { pos ->
                candidateStrip?.getSuggestion(pos)?.let { commitText(it) }
            }

            // Keyboard rows
            val keyboardContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            root.addView(keyboardContainer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            keyboardContainer.addView(buildRow(44, 0, listOf(
                key("Q"), key("W"), key("E"), key("R"), key("T"), key("Y"),
                key("U"), key("I"), key("O"), key("P")
            )))

            keyboardContainer.addView(buildRow(44, dp2px(4), listOf(
                key("A"), key("S"), key("D"), key("F"), key("G"), key("H"),
                key("J"), key("K"), key("L")
            )))

            keyboardContainer.addView(buildRow(44, dp2px(4), listOf(
                key("CAPS", weight = 2f, size = 12f),
                key("Z"), key("X"), key("C"), key("V"), key("B"),
                key("N"), key("M"),
                key("DEL", weight = 2f, size = 14f, color = 0xFFF87171)
            ))).let { row ->
                capsButton = row.getChildAt(0) as? Button
            }

            keyboardContainer.addView(buildRow(44, 0, listOf(
                key("SPACE", weight = 1.5f, size = 12f, color = 0xFF94A3B8),
                key("SEND", weight = 1f, size = 12f, color = 0xFFFFFFFF, bg = 0xFF16A34A),
                key("Aa", weight = 1f, size = 14f),
                key("SET", weight = 1f, size = 14f, color = 0xFFFBBF24)
            )))

            // Attach click listeners
            attachListeners(keyboardContainer)

            inputView = root
            Log.d("AIKeyboard", "onCreateInputView: success")
            root
        } catch (e: Exception) {
            Log.e("AIKeyboard", "onCreateInputView 异常", e)
            createFallbackView(e)
        }
    }

    // ============ 键盘构建辅助 ============

    data class KeyDef(
        val text: String,
        val weight: Float = 1f,
        val size: Float = 16f,
        val color: Int = 0xFFFFFFFF,
        val bg: Int = 0xFF1E3A5F
    )

    private fun key(
        text: String, weight: Float = 1f, size: Float = 16f,
        color: Int = 0xFFFFFFFF, bg: Int = 0xFF1E3A5F
    ) = KeyDef(text, weight, size, color, bg)

    private fun buildRow(rowHeightDp: Int, hPadding: Int, keys: List<KeyDef>): LinearLayout {
        val rowHeight = dp2px(rowHeightDp)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            height = rowHeight
            setPadding(hPadding, 0, hPadding, 0)
        }
        for (kd in keys) {
            val btn = Button(this).apply {
                text = kd.text
                textSize = kd.size
                setTextColor(kd.color)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                minimumHeight = 0
                minimumWidth = 0
                setBackgroundColor(kd.bg)
            }
            val params = LinearLayout.LayoutParams(0, rowHeight, kd.weight)
            params.setMargins(1, 1, 1, 1)
            row.addView(btn, params)
        }
        return row
    }

    private fun attachListeners(container: LinearLayout) {
        for (c in 0 until container.childCount) {
            val row = container.getChildAt(c) as LinearLayout
            for (i in 0 until row.childCount) {
                val btn = row.getChildAt(i) as Button
                val label = btn.text.toString()
                btn.setOnClickListener {
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

    // ============ 键盘操作 ============

    private fun createFallbackView(e: Exception): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF22C55E.toInt())
            minimumHeight = 220
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layout.addView(TextView(this).apply {
            text = "键盘加载失败\n错误: ${e.javaClass.simpleName}: ${e.message}"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 8)
        }, params)
        val stackLines = e.stackTrace.take(10).joinToString("\n") { it.toString() }
        layout.addView(TextView(this).apply {
            text = stackLines
            textSize = 10f
            setTextColor(0xFF000000.toInt())
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
        if (isCaps) {
            btn.setBackgroundColor(0xFF2563EB.toInt())
            btn.setTextColor(0xFFFFFFFF.toInt())
        } else {
            btn.setBackgroundColor(0xFF1E3A5F.toInt())
            btn.setTextColor(0xFFFFFFFF.toInt())
        }
        Log.d("AIKeyboard", "toggleCaps: isCaps=$isCaps")
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