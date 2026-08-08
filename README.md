# 🤖 AI 输入法

基于云端大模型的 Android 原生输入法，内置 AI 角色技能（恋爱/商务场景化回复）。

## 架构

```
app/src/main/
├── AIKeyboardService.kt     ← 输入法核心服务（按键处理 + 候选词）
├── ai/
│   ├── AIClient.kt          ← HTTP 调用云端 AI
│   ├── AISkill.kt           ← 技能抽象接口
│   └── skills/
│       ├── LoveSkills.kt    ← 6 个恋爱角色
│       └── BusinessSkills.kt ← 5 个商务角色
├── ui/
│   ├── CandidateStrip.kt    ← AI 候选词栏（自定义 View）
│   └── SettingsActivity.kt  ← 设置页（API 配置 + Skill 选择）
├── util/
│   └── Preferences.kt       ← 配置持久化
└── res/
    ├── layout/
    │   ├── keyboard_view.xml       ← 键盘布局
    │   └── activity_settings.xml   ← 设置页布局
    ├── xml/
    │   └── method.xml              ← 输入法服务声明
    └── values/
        ├── strings.xml / colors.xml / themes.xml
```

## 使用说明

### 1. 环境要求
- Android Studio Arctic Fox+
- JDK 11+
- 目标设备：Android 8.0+

### 2. 配置 API

打开设置页填入你的 API 信息：

| 字段 | 说明 | 示例 |
|------|------|------|
| API URL | OpenAI 兼容接口地址 | `https://api.openai.com/v1` |
| API Key | 你的 API Key | `sk-xxxxxxxxxxxx` |
| Model | 模型名称 | `gpt-4o-mini` |

**支持的 API 提供商：**
- OpenAI (`https://api.openai.com/v1`, `gpt-4o-mini`)
- 智谱 (`https://open.bigmodel.cn/api/paas/v4`, `glm-4-flash`)
- 文心 (`https://www.volcengineapi.com`, `doubao-lite-4k`)
- DeepSeek (`https://api.deepseek.com`, `deepseek-chat`)

### 3. 构建安装

```bash
# 在 Android Studio 中打开项目，Sync Gradle
# Build → Build Bundle(s) / APK(s) → Build APK(s)
# 生成的 APK 在 app/build/outputs/apk/debug/
```

### 4. 手机上启用输入法

1. 安装 APK
2. 系统设置 → 应用 → 输入法 → 更多设置
3. 打开 "AI 输入法"
4. 默认键盘选择 "AI 输入法"
5. 回到任意输入框，即可使用

### 5. 选择 AI 角色

设置页点击 **💕 恋爱** 或 **💼 商务** 切换到对应场景，点击角色即可切换。

### 6. 使用方法

- 正常输入文字
- 顶部 **AI 候选词栏** 会显示智能回复建议
- 点击候选词即可自动填入输入框
- **⚙ 按钮** 打开设置页

## AI 角色一览

| 类型 | 角色 | 风格 |
|------|------|------|
| 💕 恋爱 | 甜系女友 🍬 | 温柔体贴，偶尔撒娇 |
| 💕 恋爱 | 高冷御姐 ❄️ | 独立自信，有气场 |
| 💕 恋爱 | 温柔知性 🌸 | 善解人意，有同理心 |
| 💕 恋爱 | 调皮捣蛋 😜 | 古灵精怪，幽默风趣 |
| 💕 恋爱 | 暧昧撩拨 🔥 | 若即若离，小暧昧 |
| 💕 恋爱 | 深情走心 💝 | 真诚深情，直击内心 |
| 💼 商务 | 商务礼貌 🤝 | 礼貌得体，有分寸 |
| 💼 商务 | 自信气场 💼 | 自信从容，有观点 |
| 💼 商务 | 幽默风趣 😄 | 轻松幽默，有梗 |
| 💼 商务 | 谈判高手 ⚖️ | 有理有据，进退有度 |
| 💼 商务 | 执行风格 📊 | 简洁高效，直奔主题 |

## 技术要点

- `InputMethodService` 实现系统级输入法
- `OkHttp` 异步调用云端 AI API
- 自定义 `CandidateStrip` View 渲染候选词栏
- `SharedPreferences` 持久化配置
- 支持 OpenAI 兼容协议的任意大模型