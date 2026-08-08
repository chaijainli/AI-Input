package com.aikb.ime

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.util.Log
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
            Log.d("AIKeyboard", "onCreateInputView: inflating keyboard_view")
            val view = layoutInflater.inflate(R.layout.keyboard_view, null, false)
            inputView = view

            val stripView = view.findViewById<View>(R.id.candidate_strip)
            candidateStrip = stripView as? CandidateStrip
            if (candidateStrip == null) {
                throw ClassCastException("candidate_strip 不是 CandidateStrip 类型，实际: ${stripView?.javaClass?.name}")
            }
            candidateStrip?.setSuggestionsCallback { pos ->
                candidateStrip?.getSuggestion(pos)?.let { commitText(it) }
            }

            letterKeys.forEach { key ->
                val resId = resources.getIdentifier("key_$key", "id", packageName)
                if (resId == 0) {
                    Log.e("AIKeyboard", "找不到按键资源: key_$key")
                }
                view.findViewById<Button>(resId)?.setOnClickListener {
                    val text = if (isCaps) key.uppercase() else key
                    commitText(text)
                    scheduleAISuggest()
                }
            }

            view.findViewById<Button>(R.id.key_shift)?.setOnClickListener { toggleCaps() }
            view.findViewById<Button>(R.id.key_delete)?.setOnClickListener { deleteLastChar() }
            view.findViewById<Button>(R.id.key_space)?.setOnClickListener { commitText(" ") }
            view.findViewById<Button>(R.id.key_send)?.setOnClickListener { sendAction() }
            view.findViewById<Button>(R.id.key_emoji)?.setOnClickListener { insertEmoji() }
            view.findViewById<Button>(R.id.key_settings)?.setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            Log.d("AIKeyboard", "onCreateInputView: success, view size=${view.width}x${view.height}")
            view
        } catch (e: Exception) {
            Log.e("AIKeyboard", "onCreateInputView 异常", e)
            createFallbackView(e)
        }
    }

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
            gravity = android.view.Gravity.CENTER
            setPadding(24, 16, 24, 8)
        }, params)
        val stackLines = e.stackTrace.take(10).joinToString("\n") { it.toString() }
        layout.addView(TextView(this).apply {
            text = stackLines
            textSize = 10f
            setTextColor(0xFF000000.toInt())
            gravity = android.view.Gravity.START
            setPadding(24, 0, 24, 16)
            maxLines = 15
        }, params)
        return layout
    }

    private fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(SpannableString(text), 1)
    }

    private fun toggleCaps() {
        isCaps = !isCaps
        val shiftBtn = inputView?.findViewById<Button>(R.id.key_shift)
        shiftBtn?.isEnabled = true
        if (isCaps) {
            shiftBtn?.setBackgroundColor(0xFF2563EB.toInt())
        } else {
            shiftBtn?.setBackgroundColor(0xFF1E293B.toInt())
        }
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
        if (raw.startsWith("[") || raw.contains("网络错误") || raw.contains("API错误") || raw.contains("模型不存在") || raw.contains("请先在设置")) {
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