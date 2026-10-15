# 群聊剧场 ChatTheater · 设计文档

> 拉一群 AI 进群，看他们吵翻天——你是导演。
> Spring Boot 3 + Vue 3 + MySQL 8 + Python 三层全栈项目。

---

## 1. 产品一句话与核心循环

**群聊剧场**是一个"AI 群聊扮演 + 导演操控"的娱乐创作平台：用户（导演）选一个场景剧本、
拉入若干 AI 人设（演员）组建剧组、开拍——AI 演员们自动开始对话、拌嘴、互怼；导演可随时
插话、下指令、扔事件卡搅局；聊天过程实时流式直播；剧目完结后进入"名场面馆"供人回放围观。

```
选场景 → 挑演员 → 开拍 → AI 自动接梗互怼
   ↑                              ↓
   │        导演插话/指令/事件卡/暂停/禁言/调节活跃度
   │                              ↓
人设工坊自造演员 ← 名场面馆围观回放 ← 完结谢幕（热度/点赞/高光）
```

三个角色：**导演**（登录用户，剧组的所有者与操控者）、**演员**（AI 扮演的人设）、
**观众**（在广场围观直播与回放完结剧目的任何人，含未登录游客）。

## 2. 核心概念词汇表

| 概念 | 含义 |
|---|---|
| 剧场 Theater | 一场群聊的容器：标题、舞台设定、剧组、热度、状态（LIVE/ENDED） |
| 剧组 Cast | 剧场里的一组人设（2~6 人），每人有活跃度滑块与禁言开关 |
| 人设 Persona | AI 演员的"剧本"：名字、头像 emoji、原型（毒舌/杠精/慈祥…）、性格、说话风格、背景、雷点 |
| 场景剧本 Scenario | 开场的舞台设定与默认导演指令（宿舍风云/相亲现场/豆腐脑甜咸之争…） |
| 导演指令 Directive | 导演插入的自由文本指令，会注入下一条 AI 发言的上下文（"突然有人提到期末挂科"） |
| 事件卡 Event Card | 预置的戏剧性事件（红包来袭/班主任查岗/断网了…），一键扔进群里 |
| 喊卡 Pause | 暂停 AI 自动发言（用户消息仍可发，AI 静默） |
| 热度 Heat | 0~100 的"吵架激烈程度"，随消息频率与情绪强度上升 |
| 名场面 Hall | 完结剧目的公开回放馆；高光消息 = 点赞数最高的发言 |
| 导演等级 Level | XP 成长体系：群演→场记→副导演→导演→金牌导演 |

## 3. 系统架构

```
┌─────────────────────────── Vue 3 前端（7 大模块 20+ 页面）───────────────────────────┐
│ 落地页 │ 创作向导 │ 聊天室(流式气泡+导演台+氛围面板) │ 人设工坊 │ 广场 │ 个人中心 │ 运营看板 │
└───────────────┬──────────────────────────── HTTP/REST ─┬──────── SSE 流式推送 ─────┘
                │                                            ▲
┌───────────────▼──────── Spring Boot 3 后端 ────────────────┴──────────────────────┐
│ auth 用户/JWT      persona 人设+市场+试聊      scenario 场景剧本                     │
│ theater 剧场 CRUD  plaza 广场/名场面/高光       me 统计/收藏        admin 运营/审核/成本 │
│ ┌──────────────── 聊天引擎 ChatEngine（本项目心脏）────────────────┐                │
│ │ 单剧场单线程事件循环 │ 加权选人 │ 上下文裁剪 │ 连发节制 │ 热度/情绪 │                │
│ └──────────┬─────────────────────────────────────────────────┘                │
│   ai-gateway（Real DeepSeek ⇄ Mock 离线演员 双通道，异常自动降级）                  │
│   moderation（DFA 敏感词 + review_log）   sse-hub（按剧场分组广播 + 心跳）           │
└──────┬─────────────────────────────────────────────────────────────┬────────────┘
       ▼                                                             ▼
   MySQL 8（13 张表）                                    DeepSeek API（OpenAI 兼容协议）
```

## 4. 数据模型（13 张表）

| 表 | 职责 | 关键约束 |
|---|---|---|
| user | 用户（导演） | username 唯一；role=USER/ADMIN；xp |
| persona | AI 人设 | owner_id NULL=官方出品；archetype 原型；status 上/下架 |
| persona_like | 人设点赞 | (user_id, persona_id) 唯一 |
| scenario | 场景剧本 | 官方预置，backdrop 舞台设定 |
| theater_group | 剧场 | status LIVE/ENDED；visibility PUBLIC/PRIVATE；heat/message_count/like_count |
| group_member | 剧组（人设×剧场） | (group_id, persona_id) 唯一；activity 0-100；is_muted |
| message | 消息流 | sender_type=USER/PERSONA/DIRECTOR/SYSTEM；emotion；is_highlight |
| message_like | 消息点赞 | (user_id, message_id) 唯一 |
| group_like / favorite | 剧场点赞 / 收藏 | 唯一键防重复 |
| director_event | 导演操作流水 | DIRECTIVE/EVENT_CARD/PAUSE/RESUME/MUTE/ACTIVITY |
| sensitive_word | 敏感词库 | word 唯一；hit_count |
| review_log | 审核日志 | BLOCK/FLAG/PASS 三种动作 |
| llm_usage | LLM 调用计量 | mock 标记、token 估算、时延、成败 |

防重复/幂等设计沿用"数据库唯一约束兜底 + 先查后动短路"的思路；计数类一律用
`UPDATE ... SET x = x + 1` 原子自增，不做"读-改-写"。

## 5. 聊天引擎设计（心脏）

**运行时状态**（内存，每剧场一份）：`GroupRuntime{ executor(单线程), paused, looping,
lastSpeaker, members(activity/muted), pendingDirectives, ended }`，`ConcurrentHashMap<groupId,
GroupRuntime>` 管理，`computeIfAbsent` 惰性创建（首次 ignite 时从 DB 加载剧组）。

**事件循环**（ignited by 用户消息/导演指令/事件卡/恢复演出）：
1. `looping` CAS 抢锁防重入；循环最多连发 8 条防"AI 自嗨"。
2. **加权选人**：候选=未禁言成员；排除上一发言者（除非只剩一人）；权重=活跃度；
   10% 概率完全随机（增加戏剧性）。
3. **上下文构建**：人设 system 提示词（人设铁律：保持角色/一次只说 1~2 句/直接输出台词）
   + 舞台设定 + 导演总指令 + 最近 24 条群聊记录 + 待处理导演指令注入。
4. **流式生成**：ai-gateway 回调 onDelta → SSE `chunk` 事件逐字推给观众；完成落库。
5. **连发判定**：用户触发首轮必回；之后按 `p = 0.35 + 平均活跃度×0.4 + 热度×0.15`（上限 0.9）
   概率决定是否继续；每次间隔 500~1700ms 模拟真人节奏。
6. **副作用**：消息落库、message_count 原子自增、热度按消息频率+情绪强度上浮、情绪启发式
   标注、SSE 广播 message/heat/emotion 事件、llm_usage 计量。

**并发模型**：每剧场一个单线程 executor，所有 DB 写入与 SSE 广播串行化，杜绝消息乱序；
跨剧场天然并行。单实例假设（多实例需把 runtime 外置，已列入边界）。

**SSE 协议**：`hello | typing | chunk | message | system | heat | ended | ping(心跳15s)`。
EventSource 无法带 Header → 认证 token 走 `?token=` 查询参数（拦截器兼容）。

## 6. AI 网关（Real ⇄ Mock 双通道）

- **Real**：DeepSeek OpenAI 兼容协议 `{base}/v1/chat/completions`，`stream:true` 逐行解析
  `data: {"choices":[{"delta":{"content":...}}]}`；配置三元组 `DEEPSEEK_API_KEY/BASE_URL/MODEL`
  与既有项目一致，留空自动切 Mock。
- **Mock 离线演员**：按人设原型（毒舌/杠精/慈祥/卷王/社恐/吃瓜/显眼包/暴躁/阴阳/财迷）
  的模板库生成台词，支持引用上一发言者与导演指令，逐字模拟流式；**无 key 全功能可玩**。
- **降级策略**：Real 调用异常（网络/402/超时）→ 自动 fallback Mock 出演 + 系统气泡提示
  "AI 上游异常，已切换离线演员"，llm_usage 记 ok=0。产品永不因上游故障开天窗。
- **成本**：每条消息计量 prompt/completion token（stream 无 usage 时按字符数估算），
  运营看板出成本曲线与 Mock 占比。

## 7. 审核与安全

- JWT 登录态（Header Bearer 与 SSE 的 ?token= 双通道）；BCrypt 密码哈希。
- **DFA 敏感词**（前缀树）：用户消息/导演指令/人设创建全走过滤，命中即 400 BLOCK 并写
  review_log；AI 生成内容同样过闸，命中替换为拦截占位。
- **越权矩阵**：导演台操作与完结仅 owner（403）；PUBLIC 剧场任意登录用户可发言（群聊的
  热闹本质）；PRIVATE 仅 owner；admin 接口校验 role。
- 运营端：敏感词 CRUD、审核日志、人设封禁（下架即从市场消失）。

## 8. 导演成长体系

动作加 XP：开拍+10 / 发言+1 / 造人设+5 / 人设被采用+3 / 剧场被赞+5 / 被收藏+3。
等级门槛：群演 0 → 场记 50 → 副导演 200 → 导演 500 → 金牌导演 1200。

## 9. API 清单（33 个端点）

| 组 | 端点 |
|---|---|
| auth | POST /api/auth/register、POST /api/auth/login、GET/PUT /api/users/me |
| persona | GET /api/personas（市场，筛选/分页）、GET /{id}、POST、PUT /{id}、DELETE /{id}、POST /{id}/like、GET /mine、POST /{id}/try-chat、GET /archetypes |
| scenario | GET /api/scenarios、GET /{id} |
| theater | POST /api/theaters、GET /mine、GET /{id}、GET /{id}/messages、POST /{id}/messages、GET /{id}/stream(SSE)、POST /{id}/director、POST /{id}/end、POST /{id}/like、POST /{id}/favorite |
| message | POST /api/messages/{id}/like |
| plaza | GET /api/plaza/live、GET /ended、GET /{id}、GET /{id}/highlights、GET /stats |
| me | GET /api/me/overview、GET /api/me/favorites |
| admin | GET /api/admin/overview、GET /cost、GET/POST/DELETE review/words、GET /review/logs、GET /review/personas、PUT /review/personas/{id}/status |

## 10. 三期里程碑 ↔ 模块映射

| 期 | 内容 | 对应模块 |
|---|---|---|
| M1 核心闭环 | 建组→AI 自动对话→导演操控→流式观看 | auth/scenario/persona(基础)/theater/engine/ai-gateway/sse |
| M2 生态 | 人设工坊+市场+试聊、广场直播与名场面回放、敏感词、Mock 降级、收藏点赞高光 | persona(市场)/plaza/moderation/me |
| M3 质量与运营 | Python 接口自动化 + 人设守擂评测、运营看板（趋势/成本/审核）、Docker 一键部署 | qa/、admin、docker-compose、docs |

## 11. 边界与已知取舍

- 单实例假设：聊天引擎 runtime 在内存；水平扩展需引入 Redis Pub/Sub 广播 + 外置调度。
- 情绪标注为启发式（关键词/标点/原型偏置），非模型分类——可控、可解释、零成本。
- token 计量在流式无 usage 返回时按字符估算，账单以供应商控制台为准。
- AI 内容合规：输出过 DFA；上线建议叠加供应商侧内容安全过滤。
