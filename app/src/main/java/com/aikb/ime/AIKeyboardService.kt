package com.aikb.ime

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import com.aikb.ime.ai.AIClient
import com.aikb.ime.ai.AISkill
import com.aikb.ime.ai.skills.BusinessSkills
import com.aikb.ime.ai.skills.LoveSkills
import com.aikb.ime.ui.CandidateStrip
import com.aikb.ime.ui.SettingsActivity
import com.aikb.ime.util.Preferences

class AIKeyboardService : android.inputmethodservice.InputMethodService() {

    private lateinit var candidateStrip: CandidateStrip
    private var isCaps = false
    private var emojiIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private var aiRunnable: Runnable? = null
    private val DEBOUNCE_MS = 500L

    // 修复：请求序号，防止旧请求覆盖新结果
    @Volatile private var currentRequestId = 0

    private val letterKeys = listOf("q","w","e","r","t","y","u","i","o","p",
        "a","s","d","f","g","h","j","k","l",
        "z","x","c","v","b","n","m")

    override fun onCreate() {
        super.onCreate()
        Preferences.init(this)
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)

        candidateStrip = view.findViewById(R.id.candidate_strip)
        candidateStrip.setSuggestionsCallback { pos ->
            candidateStrip.getSuggestion(pos)?.let { commitText(it) }
        }

        letterKeys.forEach { key ->
            val resId = resources.getIdentifier("key_$key", "id", packageName)
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

        return view
    }

    private fun toggleCaps() {
        isCaps = !isCaps
        val shiftBtn = view?.findViewById<Button>(R.id.key_shift) ?: return
        shiftBtn.isEnabled = true
        shiftBtn.backgroundTintList = if (isCaps) {
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2563EB"))
        } else {
            null
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
        val skill = findSkill(Preferences.activeSkill) ?: return
        val ic = currentInputConnection
        val text = ic?.getTextBeforeCursor(80, 0)?.toString() ?: return
        if (text.isBlank()) return

        // 给当前请求分配唯一序号
        val requestId = ++currentRequestId

        val prompt = skill.suggestionPrompt(text, emptyList())
        AIClient.generate(skill.systemPrompt(), prompt) { result ->
            handler.post {
                // 只接受最新请求的结果，丢弃旧的
                if (requestId == currentRequestId) {
                    parseSuggestions(result)?.let { candidateStrip.setSuggestions(it) }
                }
            }
        }
    }

    private fun parseSuggestions(raw: String): List<String>? {
        // 过滤 AI 错误信息
        if (raw.startsWith("[") || raw.contains("网络错误") || raw.contains("API错误") || raw.contains("请先在设置")) {
            return null
        }

        val lines = raw.split("\n")
            .map { it.trim().removePrefix("-").removePrefix("•").removePrefix("·").trim() }
            .filter { it.isNotBlank() && it.length <= 50 }
            .take(5)
        return if (lines.isNotEmpty()) lines else null
    }

    private fun findSkill(id: String): AISkill? {
        val all = (LoveSkills.all() + BusinessSkills.all()) as List<AISkill>
        return all.find { it.id == id } ?: all.firstOrNull()
    }
}