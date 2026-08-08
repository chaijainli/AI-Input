package com.aikb.ime.ai

/** AI 技能接口 */
interface AISkill {
    /** 技能唯一 ID */
    val id: String
    /** 显示名称 */
    val name: String
    /** Emoji 图标 */
    val icon: String
    /** 角色类型：love / business */
    val category: String
    /** 角色描述 */
    val description: String

    /** 生成系统提示词 */
    fun systemPrompt(): String

    /** 生成候选词提示（基于用户输入生成回复建议） */
    fun suggestionPrompt(userInput: String, existingSuggestions: List<String>): String
}

/** 恋爱类技能 */
abstract class LoveSkill : AISkill {
    override val category: String = "love"
}

/** 商务类技能 */
abstract class BusinessSkill : AISkill {
    override val category: String = "business"
}