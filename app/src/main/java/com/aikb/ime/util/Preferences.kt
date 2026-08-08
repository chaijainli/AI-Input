package com.aikb.ime.util

import android.content.Context
import android.content.SharedPreferences

/** 配置持久化 */
object Preferences {
    private const val PREFS_NAME = "aikb_prefs"
    private const val KEY_API_URL = "api_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_ACTIVE_SKILL = "active_skill"

    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    private fun prefs(): SharedPreferences =
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: throw IllegalStateException("Preferences not initialized. Call Preferences.init() first.")

    var apiUrl: String
        get() = prefs().getString(KEY_API_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(v) { prefs().edit().putString(KEY_API_URL, v).apply() }

    var apiKey: String
        get() = prefs().getString(KEY_API_KEY, "") ?: ""
        set(v) { prefs().edit().putString(KEY_API_KEY, v).apply() }

    var model: String
        get() = prefs().getString(KEY_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
        set(v) { prefs().edit().putString(KEY_MODEL, v).apply() }

    var activeSkill: String
        get() = prefs().getString(KEY_ACTIVE_SKILL, "smart_reply") ?: "smart_reply"
        set(v) { prefs().edit().putString(KEY_ACTIVE_SKILL, v).apply() }

    const val DEFAULT_URL = "https://api.openai.com/v1"
}