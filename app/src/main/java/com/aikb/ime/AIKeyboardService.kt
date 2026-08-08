package com.aikb.ime

import android.content.Intent
import android.graphics.drawable.GradientDrawable
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
import com.aikb.ime.pinyin.PinyinDict
import com.aikb.ime.ui.CandidateStrip
import com.aikb.ime.ui.SettingsActivity
import com.aikb.ime.util.Preferences

class AIKeyboardService : android.inputmethodservice.InputMethodService() {

    companion object {
        private val BG_ROOT = 0xFF22C55E.toInt()
        private val BG_KEY = 0xFF1E3A5F.toInt()
        private val BG_CAPS_ON = 0xFF2563EB.toInt()
        private val BG_MODE_CN = 0xFF2563EB.toInt()
        private val BG_SEND = 0xFF16A34A.toInt()
        private val C_WHITE = 0xFFFFFFFF.toInt()
        private val C_DEL = 0xFFF87171.toInt()
        private val C_SPACE = 0xFF94A3B8.toInt()
        private val C_SET = 0xFFFBBF24.toInt()
        private val C_FALLBACK_TEXT = 0xFF000000.toInt()
    }

    private var candidateStrip: CandidateStrip? = null
    private var capsButton: Button? = null
    private var modeButton: Button? = null
    private var isCaps = false
    private var inputMode = "cn"  // cn / en / num
    private var pinyinBuffer = ""

    private val handler = Handler(Looper.getMainLooper())
    private var aiRunnable: Runnable? = null
    private val DEBOUNCE_MS = 400L

    @Volatile private var currentRequestId = 0

    private val letterKeys = listOf("q","w","e","r","t","y","u","i","o","p",
        "a","s","d","f","g","h","j","k","l",
        "z","x","c","v","b","n","m")

    override fun onCreate() {
        super.onCreate()
        try {
            Preferences.init(this)
            PinyinDict.init(this)
        } catch (e: Exception) {
            Log.e("AIKeyboard", "onCreate init 失败", e)
        }
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
                LinearLayout.LayoutParams.MATCH_PARENT, dp2px(48), 0f
            ).apply { topMargin = dp2px(4); leftMargin = dp2px(4); rightMargin = dp2px(4) })
            candidateStrip = strip
            candidateStrip?.setSuggestionsCallback { pos ->
                clearComposing()
                candidateStrip?.getSuggestion(pos)?.let { word ->
                    PinyinDict.recordSelection(word, pinyinBuffer)
                    commitText(word)
                }
            }

            val keyboardContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            root.addView(keyboardContainer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            // 数字行：1234567890（1-9 同时是标点 ,.;!?:()"）
            val numRow = buildRow(34, dp2px(4), listOf(
                k("1"), k("2"), k("3"), k("4"), k("5"),
                k("6"), k("7"), k("8"), k("9"), k("0")
            ))
            numRow.visibility = View.GONE
            keyboardContainer.addView(numRow)

            // 字母行 1
            val row1 = buildRow(46, 0, listOf(
                k("Q"), k("W"), k("E"), k("R"), k("T"), k("Y"),
                k("U"), k("I"), k("O"), k("P")
            ))
            keyboardContainer.addView(row1)

            // 字母行 2
            keyboardContainer.addView(buildRow(46, dp2px(4), listOf(
                k("A"), k("S"), k("D"), k("F"), k("G"), k("H"),
                k("J"), k("K"), k("L")
            )))

            // 字母行 3：MODE + Z~M + DEL
            val row3 = buildRow(46, dp2px(4), listOf(
                k("中", 1.5f, 13f, C_WHITE, BG_MODE_CN),
                k("CAPS", 1f, 11f),
                k("Z"), k("X"), k("C"), k("V"), k("B"),
                k("N"), k("M"),
                k("DEL", 1.5f, 14f, C_DEL)
            ))
            keyboardContainer.addView(row3)
            capsButton = row3.getChildAt(1) as? Button
            modeButton = row3.getChildAt(0) as? Button

            // 功能行
            keyboardContainer.addView(buildRow(46, 0, listOf(
                k("123", 1f, 12f, C_WHITE, BG_MODE_CN),
                k("SPACE", 2f, 12f, C_SPACE),
                k("SEND", 1.5f, 12f, C_WHITE, BG_SEND),
                k("SET", 1f, 12f, C_SET)
            )))

            updateModeUI()
            attachListeners(keyboardContainer)
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
                setBackground(createRoundBg(kd.bgColor, dp2pxF(6f)))
            }
            val params = LinearLayout.LayoutParams(0, dp2px(rowHeightDp), kd.weight)
            params.setMargins(1, 2, 1, 2)
            row.addView(button, params)
        }
        return row
    }

    private fun createRoundBg(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    private fun dp2pxF(dp: Float) = dp * resources.displayMetrics.density

    private fun attachListeners(container: LinearLayout) {
        for (c in 0 until container.childCount) {
            val row = container.getChildAt(c) as LinearLayout
            for (i in 0 until row.childCount) {
                val button = row.getChildAt(i) as Button
                val label = button.text.toString()
                button.setOnClickListener { handleKey(label) }
            }
        }
    }

    private fun handleKey(label: String) {
        when (label) {
            "DEL" -> { deleteLastChar() }
            "SPACE" -> { commitText(" "); clearComposing() }
            "SEND" -> sendAction()
            "CAPS" -> toggleCaps()
            "SET" -> startActivity(Intent(this, SettingsActivity::class.java))
            "中" -> { toggleMode() }
            "123" -> { toggleMode() }
            else -> {
                when (inputMode) {
                    "cn" -> handleCN(label)
                    "en" -> handleEN(label)
                    "num" -> handleNum(label)
                }
            }
        }
    }

    private fun handleCN(label: String) {
        val lower = label.lowercase()
        if (lower.length == 1 && lower in letterKeys) {
            pinyinBuffer += lower
            updateComposing()
            scheduleSuggestions()
        } else if (label in "1234567890") {
            pinyinBuffer += label
            updateComposing()
            scheduleSuggestions()
        }
    }

    private fun handleEN(label: String) {
        val lower = label.lowercase()
        if (lower.length == 1 && lower in letterKeys) {
            clearComposing()
            val text = if (isCaps) lower.uppercase() else lower
            commitText(text)
        } else if (label in "1234567890") {
            clearComposing()
            val map = mapOf("1" to "!", "2" to "@", "3" to "#", "4" to "$",
                "5" to "%", "6" to "^", "7" to "&", "8" to "*", "9" to "(", "0" to ")")
            commitText(map[label] ?: label)
        }
    }

    private fun handleNum(label: String) {
        clearComposing()
        val map = mapOf(
            "1" to ",", "2" to ".", "3" to ";", "4" to "!",
            "5" to "?", "6" to ":", "7" to "\"", "8" to "(", "9" to ")", "0" to " "
        )
        commitText(map[label] ?: label)
    }

    private fun toggleMode() {
        when (inputMode) {
            "cn" -> { inputMode = "en" }
            "en" -> { inputMode = "num" }
            "num" -> { inputMode = "cn" }
        }
        updateModeUI()
        clearComposing()
    }

    private fun updateModeUI() {
        val btn = modeButton ?: return
        when (inputMode) {
            "cn" -> { btn.text = "中"; btn.setTextColor(C_WHITE) }
            "en" -> { btn.text = "EN"; btn.setTextColor(C_WHITE) }
            "num" -> { btn.text = "123"; btn.setTextColor(C_WHITE) }
        }
    }

    private fun updateComposing() {
        currentInputConnection?.setComposingText(pinyinBuffer, 1)
    }

    private fun clearComposing() {
        if (pinyinBuffer.isEmpty()) return
        pinyinBuffer = ""
        currentInputConnection?.setComposingText("", 1)
    }

    private fun toggleCaps() {
        isCaps = !isCaps
        val btn = capsButton ?: return
        btn.setBackgroundColor(if (isCaps) BG_CAPS_ON else BG_KEY)
        btn.setTextColor(C_WHITE)
    }

    private fun deleteLastChar() {
        if (pinyinBuffer.isNotEmpty()) {
            pinyinBuffer = pinyinBuffer.dropLast(1)
            updateComposing()
            if (pinyinBuffer.isEmpty()) {
                candidateStrip?.setSuggestions(emptyList())
            } else {
                scheduleSuggestions()
            }
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
            scheduleSuggestions()
        }
    }

    private fun sendAction() {
        clearComposing()
        currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_SEND)
    }

    private fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(SpannableString(text), 1)
    }

    private fun scheduleSuggestions() {
        aiRunnable?.let { handler.removeCallbacks(it) }
        aiRunnable = Runnable { fetchSuggestions() }
        handler.postDelayed(aiRunnable!!, DEBOUNCE_MS)
    }

    private fun fetchSuggestions() {
        if (inputMode != "cn") return
        val buffer = pinyinBuffer
        if (buffer.isEmpty()) return

        try {
            // 优先用本地拼音词库
            val local = PinyinDict.lookup(buffer)
            if (local.isNotEmpty()) {
                candidateStrip?.setSuggestions(local.take(3))
                return
            }

            // 词库无匹配且长度 >= 2 才发 AI 请求
            if (buffer.length < 2) return
            val skill = findSkill(Preferences.activeSkill) ?: return
            val requestId = ++currentRequestId
            val prompt = skill.suggestionPrompt("拼音输入：「$buffer」", emptyList())
            AIClient.generate(skill.systemPrompt(), prompt) { result ->
                handler.post {
                    if (requestId == currentRequestId) {
                        parseSuggestions(result)?.let {
                            candidateStrip?.setSuggestions(it)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AIKeyboard", "fetchSuggestions 异常", e)
        }
    }

    private fun parseSuggestions(raw: String): List<String>? {
        if (raw.startsWith("[") || raw.contains("网络错误") || raw.contains("API错误") ||
            raw.contains("模型不存在") || raw.contains("请先在设置")) {
            return null
        }
        val lines = raw.split("\n")
            .map {
                it.trim()
                    .removePrefix("-")
                    .removePrefix("•")
                    .removePrefix("·")
                    .trim()
                    .replace(Regex("^\\d+[\\.\\．、]"), "").trim()
                    .replace(Regex("^[^：:：\\-—\\s]+[：:：\\-—]"), "").trim()
            }
            .filter { it.isNotBlank() && it.length <= 50 }
            .take(3)
        return if (lines.isNotEmpty()) lines else null
    }

    private fun findSkill(id: String): AISkill? {
        val all = LoveSkills.all().map { it as AISkill } + BusinessSkills.all().map { it as AISkill }
        return all.find { it.id == id } ?: all.firstOrNull()
    }

    override fun onDestroy() {
        aiRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun dp2px(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()

    private fun createFallbackView(e: Exception): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_ROOT)
            minimumHeight = 220
        }
        layout.addView(TextView(this).apply {
            text = "键盘加载失败\n错误: ${e.javaClass.simpleName}: ${e.message}"
            textSize = 16f
            setTextColor(C_WHITE)
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 8)
        })
        layout.addView(TextView(this).apply {
            text = e.stackTrace.take(8).joinToString("\n") { it.toString() }
            textSize = 10f
            setTextColor(C_FALLBACK_TEXT)
            gravity = Gravity.START
            setPadding(24, 0, 24, 16)
            maxLines = 10
        })
        return layout
    }
}