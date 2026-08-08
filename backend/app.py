"""AI输入法 - 后端服务"""
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import json
import time
from datetime import datetime
from typing import Optional

app = FastAPI(title="AI输入法后端")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ========== 配置 ==========
# 替换为你的OpenAI兼容API Key和地址
# 支持: OpenAI, 智谱, 文心, DeepSeek, ChatGLM等
OPENAI_API_KEY = "YOUR_API_KEY_HERE"
OPENAI_BASE_URL = "https://api.openai.com/v1"
AI_MODEL = "gpt-4o-mini"

# ========== AI Skill 模块 ==========
SKILLS = {}

# 恋爱Skill
LOVE_PERSONAS = {
    "sweet": {
        "name": "甜系女友",
        "system": "你是一个温柔体贴、善解人意的甜美女友。回复风格：语气可爱、偶尔撒娇、用emoji表达情绪。内容要真诚、有感情深度，适合在恋爱中用来回复另一半的消息。回复要自然、不做作，像真人聊天一样。每次回复不超过80字。"
    },
    "cool": {
        "name": "高冷御姐",
        "system": "你是一个有气场、独立自信的酷女孩。回复风格：简洁有力、偶尔带点傲娇、有主见。不会过度依赖对方，有自己的想法和态度。回复要简短但要有质感，像高冷御姐聊天一样。每次回复不超过60字。"
    },
    "gentle": {
        "name": "温柔知性",
        "system": "你是一个温柔知性、善解人意的女孩。回复风格：语气温和、有同理心、会关心对方。内容真诚温暖，让人感到被理解和被支持。像温柔的女友或闺蜜一样聊天。每次回复不超过80字。"
    },
    "playful": {
        "name": "调皮捣蛋",
        "system": "你是一个古灵精怪、活泼可爱的调皮女友。回复风格：幽默风趣、喜欢开玩笑、偶尔怼人但很可爱。内容轻松有趣，让对方忍不住笑。每次回复不超过80字。"
    },
}

BUSINESS_PERSONAS = {
    "polite": {
        "name": "商务礼貌",
        "system": "你是一个专业得体的商务人士。回复风格：礼貌、正式但不刻板、有分寸感。适合回复客户、领导、合作伙伴。内容要专业、有商务素养。每次回复不超过100字。"
    },
    "confident": {
        "name": "自信气场",
        "system": "你是一个气场强大、自信从容的商务人士。回复风格：有自信但不傲慢、有观点但不咄咄逼人。适合需要展现个人气场和决策力的场合。每次回复不超过100字。"
    },
    "humorous": {
        "name": "幽默风趣",
        "system": "你是一个幽默风趣的商务人士。回复风格：轻松幽默、有梗但不低俗、能在商务场合拉近关系。适合和客户或同事破冰、活跃气氛。每次回复不超过100字。"
    },
}

# ========== 请求/响应模型 ==========
class ReplyRequest(BaseModel):
    user_message: str
    skill_type: str = "love"          # "love" | "business"
    persona: str = "sweet"
    context: str = ""                 # 之前的对话上下文
    temperature: float = 0.8

class SuggestRequest(BaseModel):
    prefix: str                       # 用户输入的前缀
    max_tokens: int = 30

class HealthResponse(BaseModel):
    status: str
    timestamp: str
    version: str

# ========== 核心AI调用 ==========
def call_ai_api(system_prompt: str, user_prompt: str, temperature: float = 0.8) -> str:
    """调用云端AI API（支持OpenAI兼容接口）"""
    try:
        import urllib.request
        import urllib.error

        if OPENAI_API_KEY == "YOUR_API_KEY_HERE":
            return "[请先在config.py中设置API Key]"

        payload = json.dumps({
            "model": AI_MODEL,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            "temperature": temperature,
            "max_tokens": 200
        }).encode("utf-8")

        req = urllib.request.Request(
            f"{OPENAI_BASE_URL}/chat/completions",
            data=payload,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {OPENAI_API_KEY}"
            }
        )

        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return data["choices"][0]["message"]["content"].strip()
    except Exception as e:
        return f"[调用失败: {str(e)[:50]}]"

# ========== API 路由 ==========
@app.get("/health", response_model=HealthResponse)
async def health():
    return HealthResponse(
        status="ok",
        timestamp=datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        version="1.0.0"
    )

@app.get("/skills")
async def list_skills():
    """获取可用的AI角色列表"""
    skills = []
    for persona_id, persona in LOVE_PERSONAS.items():
        skills.append({"type": "love", "id": persona_id, **persona})
    for persona_id, persona in BUSINESS_PERSONAS.items():
        skills.append({"type": "business", "id": persona_id, **persona})
    return {"skills": skills}

@app.post("/reply")
async def get_reply(req: ReplyRequest):
    """生成AI智能回复"""
    if req.skill_type == "love":
        persona = LOVE_PERSONAS.get(req.persona)
    elif req.skill_type == "business":
        persona = BUSINESS_PERSONAS.get(req.persona)
    else:
        raise HTTPException(status_code=400, detail="无效的skill类型")

    if not persona:
        raise HTTPException(status_code=400, detail="无效的角色ID")

    user_prompt = f"对方发来消息：「{req.user_message}」\n\n请生成回复："
    if req.context:
        user_prompt = f"对话上下文：{req.context}\n\n对方最新消息：「{req.user_message}」\n\n请生成回复："

    reply = call_ai_api(persona["system"], user_prompt, req.temperature)
    return {"reply": reply, "persona": persona["name"], "skill_type": req.skill_type}

@app.post("/suggest")
async def suggest_text(req: SuggestRequest):
    """根据前缀生成续写建议"""
    if not req.prefix:
        return {"suggestions": []}

    suggestions = []
    # 简单本地续写（不依赖AI）
    local_suggestions = [
        req.prefix + "吗？",
        req.prefix + "！今天过得怎么样？",
        req.prefix + "，你在做什么呢？",
        req.prefix + "～记得吃饭哦～",
    ]
    for s in local_suggestions[:3]:
        if len(s) <= req.max_tokens + len(req.prefix):
            suggestions.append(s)

    # 可选：调用AI生成更智能的续写
    if OPENAI_API_KEY != "YOUR_API_KEY_HERE":
        try:
            ai_suggest = call_ai_api(
                "你是一个中文续写助手。用户输入了一个文本前缀，请生成3个不同的续写建议，每行一个，每条不超过50字。",
                f"前缀：「{req.prefix}」\n请续写："
            )
            lines = [l.strip() for l in ai_suggest.split("\n") if l.strip()]
            for line in lines[:3]:
                if not any(s.startswith(line[:5]) for s in suggestions):
                    suggestions.append(line)
        except:
            pass

    return {"suggestions": suggestions[:5]}

# ========== 启动 ==========
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8899)