"""剧场模块：建组约束 / 自动对话 / 导演台 / SSE / 完结 / 高光 / 越权。"""
import json
import time

import pytest

from conftest import Api, wait_ai_messages


def pick_personas(api, n):
    page = Api.ok(api.get("/api/personas", params={"size": 50, "sort": "hot"}))
    return [p["id"] for p in page["records"][:n]]


def create_theater(api, persona_ids=None, visibility="PRIVATE", title=None, scenario_id=1):
    ids = persona_ids or pick_personas(api, 3)
    return Api.ok(api.post("/api/theaters", {
        "title": title or "QA 自动化剧场",
        "scenarioId": scenario_id,
        "personaIds": ids,
        "visibility": visibility,
        "directorNote": None,
    }))


class TestCreate:
    def test_create_ok(self, user_a):
        """建组成功：剧组 3 人、状态 LIVE、种子场景快照。"""
        t = create_theater(user_a["api"])
        assert t["id"] > 0
        assert t["status"] == "LIVE"
        assert t["ownerId"] == user_a["user"]["id"]
        assert len(t["members"]) == 3
        assert t["scenarioSnapshot"], "场景快照必须随组保存"
        for m in t["members"]:
            assert m["activity"] >= 0
            assert m["muted"] is False

    def test_create_too_few_personas(self, user_a):
        ids = pick_personas(user_a["api"], 1)
        r = user_a["api"].post("/api/theaters", {
            "title": "单人不成戏", "scenarioId": 1, "personaIds": ids, "visibility": "PRIVATE",
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "少于 2 位演员应被拒绝"

    def test_create_too_many_personas(self, user_a):
        ids = pick_personas(user_a["api"], 7)
        if len(ids) < 7:
            pytest.skip("市场不足 7 位演员，跳过超编用例")
        r = user_a["api"].post("/api/theaters", {
            "title": "超编剧组", "scenarioId": 1, "personaIds": ids, "visibility": "PRIVATE",
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "超过 6 位演员应被拒绝"

    def test_guest_cannot_create(self, guest):
        r = guest.post("/api/theaters", {"title": "x", "scenarioId": 1, "personaIds": [1, 2], "visibility": "PRIVATE"})
        assert r.status_code == 401


class TestAutoChat:
    def test_ai_auto_open(self, user_a):
        """建组后 AI 演员自动开吵（Mock 降级也能演）。"""
        t = create_theater(user_a["api"], title="自动开演测试")
        msgs = wait_ai_messages(user_a["api"], t["id"], expect=2, timeout=25)
        persona_msgs = [m for m in msgs if m["senderType"] == "PERSONA"]
        assert len(persona_msgs) >= 2, "AI 应在无人工干预下自动发言"
        for m in persona_msgs:
            assert m["content"], "台词不能为空"
            assert m["emotion"] in ("joy", "angry", "sad", "shock", "jealousy", "pride", "neutral")

    def test_user_message_triggers_reply(self, user_a):
        """观众发言后 AI 会接话。"""
        t = create_theater(user_a["api"], title="插话回怼测试")
        wait_ai_messages(user_a["api"], t["id"], expect=1, timeout=20)
        mine = Api.ok(user_a["api"].post(f"/api/theaters/{t['id']}/messages", {"content": "QA 测试观众发言，都别演了！"}))
        assert mine["senderType"] == "USER"
        assert mine["senderName"]
        msgs = wait_ai_messages(user_a["api"], t["id"], expect=1, timeout=25)
        assert any(m["senderType"] == "USER" and m["id"] == mine["id"] for m in msgs)

    def test_message_history_asc(self, user_a):
        """历史消息按时间升序返回。"""
        t = create_theater(user_a["api"], title="历史顺序测试")
        msgs = wait_ai_messages(user_a["api"], t["id"], expect=1, timeout=20)
        ids = [m["id"] for m in msgs]
        assert ids == sorted(ids), "消息应按 id 升序"


class TestDirector:
    """导演台七种操作 + 越权矩阵。"""

    def test_directive(self, user_a):
        """下指令：产生 DIRECTOR 消息并注入引擎。"""
        t = create_theater(user_a["api"], title="指令测试")
        r = Api.ok(user_a["api"].post(f"/api/theaters/{t['id']}/director", {
            "type": "DIRECTIVE", "content": "QA 指令：大家都来聊聊加班文化",
        }))
        assert r["message"]["senderType"] == "DIRECTOR"
        assert r["message"]["content"].startswith("QA 指令")

    def test_event_card(self, user_a):
        """扔事件卡：产生 SYSTEM 广播，Mock 演员有专属反应。"""
        t = create_theater(user_a["api"], title="事件卡测试")
        r = Api.ok(user_a["api"].post(f"/api/theaters/{t['id']}/director", {
            "type": "EVENT_CARD", "eventKey": "红包",
        }))
        assert r["card"]["key"] == "红包"
        assert r["message"]["senderType"] == "SYSTEM"
        assert "红包" in r["message"]["content"]

    def test_event_card_unknown(self, user_a):
        t = create_theater(user_a["api"], title="未知卡测试")
        r = user_a["api"].post(f"/api/theaters/{t['id']}/director", {"type": "EVENT_CARD", "eventKey": "不存在的卡"})
        _, code, _ = Api.biz_err(r)
        assert code != 0

    def test_mute_and_activity(self, user_a):
        """禁言 / 解禁 / 活跃度调节。"""
        api = user_a["api"]
        t = create_theater(api, title="禁言活跃度测试")
        pid = t["members"][0]["personaId"]

        r = Api.ok(api.post(f"/api/theaters/{t['id']}/director", {"type": "MUTE", "personaId": pid}))
        assert r["muted"] is True
        r = Api.ok(api.post(f"/api/theaters/{t['id']}/director", {"type": "UNMUTE", "personaId": pid}))
        assert r["muted"] is False

        r = Api.ok(api.post(f"/api/theaters/{t['id']}/director", {
            "type": "ACTIVITY", "personaId": pid, "activity": 88,
        }))
        assert r["activity"] == 88

        r = api.post(f"/api/theaters/{t['id']}/director", {"type": "ACTIVITY", "personaId": pid, "activity": 999})
        _, code, _ = Api.biz_err(r)
        assert code != 0, "活跃度超界应被拒"

    def test_pause_resume(self, user_a):
        api = user_a["api"]
        t = create_theater(api, title="暂停恢复测试")
        r = Api.ok(api.post(f"/api/theaters/{t['id']}/director", {"type": "PAUSE"}))
        assert r["paused"] is True
        r = Api.ok(api.post(f"/api/theaters/{t['id']}/director", {"type": "RESUME"}))
        assert r["paused"] is False

    def test_director_only_owner(self, user_a, user_b):
        """非导演不能操控导演台。"""
        t = create_theater(user_a["api"], title="越权测试", visibility="PUBLIC")
        r = user_b["api"].post(f"/api/theaters/{t['id']}/director", {"type": "DIRECTIVE", "content": "我不是导演"})
        _, code, _ = Api.biz_err(r)
        assert code != 0, "非 owner 的导演操作应被拒"

    def test_private_theater_invisible_to_others(self, user_a, user_b):
        """私密剧场对他人不可见。"""
        t = create_theater(user_a["api"], title="私密测试", visibility="PRIVATE")
        r = user_b["api"].get(f"/api/theaters/{t['id']}")
        _, code, _ = Api.biz_err(r)
        assert code != 0, "私密剧场应拒绝他人访问"


class TestSse:
    def test_stream_protocol(self, user_a):
        """SSE 全链路：hello 心跳协议 + 流式事件（typing/chunk/message）。"""
        import requests

        from conftest import BASE

        t = create_theater(user_a["api"], title="SSE 协议测试")
        token = None
        # 从客户端 session 中取 token
        token = user_a["api"].s.headers["Authorization"].replace("Bearer ", "")

        events = {}
        with requests.get(
            f"{BASE}/api/theaters/{t['id']}/stream",
            params={"token": token},
            stream=True,
            timeout=(5, 20),
        ) as resp:
            assert resp.status_code == 200
            assert "text/event-stream" in resp.headers.get("content-type", "")
            deadline = time.time() + 18
            current_event = None
            parsed = 0
            for raw in resp.iter_lines():
                if time.time() > deadline:
                    break
                # text/event-stream 响应无 charset：decode_unicode=True 会退回 latin-1，
                # 中文 UTF-8 字节误读出的 U+0085(NEL) 会被 str.splitlines 当作行界，
                # 把 data 行从 JSON 中间劈开。按 bytes 取行（仅按 \r\n 切分）再手动
                # UTF-8 解码即可根治（浏览器 EventSource 规范强制 UTF-8，产品无此问题）。
                line = raw.decode("utf-8", errors="replace")
                if not line:
                    current_event = None
                    if "message" in events and ("typing" in events or "chunk" in events):
                        break
                    continue
                if line.startswith("event:"):
                    current_event = line.split(":", 1)[1].strip()
                    events[current_event] = events.get(current_event, 0) + 1
                elif line.startswith("data:") and current_event == "message":
                    payload = json.loads(line.split(":", 1)[1])
                    parsed += 1
                    assert payload.get("senderType") in ("PERSONA", "USER", "SYSTEM", "DIRECTOR")
                    assert payload.get("content")

        assert "hello" in events, "SSE 必须先推 hello"
        assert "message" in events, "Mock 模式下应能收到定稿消息"
        assert parsed >= 1, "至少应完整解析一条定稿消息"

    def test_guest_stream_public(self, user_a, guest):
        """游客可围观 PUBLIC 剧场直播。"""
        import requests

        from conftest import BASE

        t = create_theater(user_a["api"], title="游客围观测试", visibility="PUBLIC")
        r = requests.get(f"{BASE}/api/theaters/{t['id']}/stream", stream=True, timeout=(5, 6))
        assert r.status_code == 200
        r.close()


class TestEnd:
    def test_end_and_highlight(self, user_a):
        """完结 → 点赞最高的台词进入名场面。"""
        api = user_a["api"]
        t = create_theater(api, title="杀青高光测试")
        wait_ai_messages(api, t["id"], expect=1, timeout=20)

        # 给某条演员台词点赞
        msgs = Api.ok(api.get(f"/api/theaters/{t['id']}/messages", params={"limit": 50}))
        target = next((m for m in msgs if m["senderType"] == "PERSONA"), None)
        assert target, "应至少有一条演员台词"
        r = Api.ok(api.post(f"/api/messages/{target['id']}/like"))
        assert r["liked"] is True

        # 完结
        r = Api.ok(api.post(f"/api/theaters/{t['id']}/end", {"reason": "QA 杀青"}))
        assert r.get("ended") is True

        detail = Api.ok(api.get(f"/api/theaters/{t['id']}"))
        assert detail["status"] == "ENDED"
        assert detail["endedReason"] == "QA 杀青"

        # 已完结不能再发言/下指令
        r2 = api.post(f"/api/theaters/{t['id']}/messages", {"content": "还想说两句"})
        _, code, _ = Api.biz_err(r2)
        assert code != 0, "杀青后发言应被拒"

    def test_end_only_owner(self, user_a, user_b):
        t = create_theater(user_a["api"], title="越权杀青测试", visibility="PUBLIC")
        r = user_b["api"].post(f"/api/theaters/{t['id']}/end", {"reason": "不怀好意"})
        _, code, _ = Api.biz_err(r)
        assert code != 0

    def test_mine_theaters(self, user_a):
        t = create_theater(user_a["api"], title="我的剧场列表")
        mine = Api.ok(user_a["api"].get("/api/theaters/mine"))
        assert any(x["id"] == t["id"] for x in mine)


class TestLikes:
    def test_theater_like_and_favorite(self, user_a, user_b):
        """剧场点赞 / 收藏 toggle + 他人可见性。"""
        api = user_a["api"]
        t = create_theater(api, title="赞藏测试", visibility="PUBLIC")

        liked = Api.ok(api.post(f"/api/theaters/{t['id']}/like"))["liked"]
        assert liked is True
        fav = Api.ok(api.post(f"/api/theaters/{t['id']}/favorite"))["favorited"]
        assert fav is True

        # 他人视角可见公开剧场的点赞数
        detail_b = Api.ok(user_b["api"].get(f"/api/theaters/{t['id']}"))
        assert detail_b["likeCount"] >= 1

        # 收藏出现在个人中心 favorites
        favorites = Api.ok(api.get("/api/me/favorites"))
        assert any(x["id"] == t["id"] for x in favorites)

        # toggle 回去
        assert Api.ok(api.post(f"/api/theaters/{t['id']}/like"))["liked"] is False
        assert Api.ok(api.post(f"/api/theaters/{t['id']}/favorite"))["favorited"] is False
