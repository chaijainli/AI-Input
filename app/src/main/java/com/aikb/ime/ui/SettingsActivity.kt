package com.aikb.ime.ui

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.*
import com.aikb.ime.R
import com.aikb.ime.ai.skills.LoveSkills
import com.aikb.ime.ai.skills.BusinessSkills
import com.aikb.ime.util.Preferences

/** 设置页面：API 配置 + Skill 选择 */
class SettingsActivity : Activity() {

    private var etUrl: EditText? = null
    private var etKey: EditText? = null
    private var etModel: EditText? = null
    private var btnSave: Button? = null
    private var skillList: ListView? = null
    private var tvCategory: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_settings)
        } catch (e: Exception) {
            Toast.makeText(this, "设置页面加载失败", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            Preferences.init(this)

            etUrl = findViewById(R.id.et_url)
            etKey = findViewById(R.id.et_key)
            etModel = findViewById(R.id.et_model)
            btnSave = findViewById(R.id.btn_save)
            skillList = findViewById(R.id.list_skills)
            tvCategory = findViewById(R.id.tv_category_title)

            etKey?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

            etUrl?.setText(Preferences.apiUrl)
            etKey?.setText(Preferences.apiKey)
            etModel?.setText(Preferences.model)

            btnSave?.setOnClickListener {
                val url = etUrl?.text?.toString()?.trim() ?: ""
                val key = etKey?.text?.toString()?.trim() ?: ""
                val model = etModel?.text?.toString()?.trim() ?: ""

                if (url.isEmpty()) {
                    Toast.makeText(this, "API URL 不能为空", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                Preferences.apiUrl = url
                Preferences.apiKey = key
                Preferences.model = model
                Toast.makeText(this, "配置已保存 ✓", Toast.LENGTH_SHORT).show()
            }

            val btnLove = findViewById<Button>(R.id.btn_love)
            btnLove?.setOnClickListener { showSkillList("love") }

            val btnBusiness = findViewById<Button>(R.id.btn_business)
            btnBusiness?.setOnClickListener { showSkillList("business") }

            showSkillList("love")
        } catch (e: Exception) {
            android.util.Log.e("Settings", "onCreate 异常", e)
            Toast.makeText(this, "设置页初始化失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSkillList(category: String) {
        tvCategory?.text = if (category == "love") "💕 恋爱场景" else "💼 商务场景"

        val skills: List<com.aikb.ime.ai.AISkill> = if (category == "love") {
            LoveSkills.all().map { it as com.aikb.ime.ai.AISkill }
        } else {
            BusinessSkills.all().map { it as com.aikb.ime.ai.AISkill }
        }

        val items = skills.map {
            val marker = if (it.id == Preferences.activeSkill) " ⬅" else ""
            "${it.icon} ${it.name}${marker}  —  ${it.description}"
        }

        skillList?.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        skillList?.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val skill = skills[position]
            Preferences.activeSkill = skill.id
            Toast.makeText(this, "已切换: ${skill.icon} ${skill.name}", Toast.LENGTH_SHORT).show()
        }
    }
}