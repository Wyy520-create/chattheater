"""人设模块：原型 / 市场 / CRUD / 点赞 / 试聊 / 越权。"""
from uuid import uuid4

from conftest import Api

ARCHE_TYPES = {"毒舌", "杠精", "慈祥", "卷王", "社恐", "吃瓜", "显眼包", "暴躁", "阴阳", "财迷"}


class TestArchetypes:
    def test_archetypes_list(self, user_a):
        arch = Api.ok(user_a["api"].get("/api/personas/archetypes"))
        assert set(arch) == ARCHE_TYPES


class TestMarket:
    def test_market_default(self, user_a):
        page = Api.ok(user_a["api"].get("/api/personas", params={"page": 1, "size": 10}))
        assert page["total"] >= 10  # 官方种子 10 位
        assert len(page["records"]) == 10
        vo = page["records"][0]
        for key in ("id", "name", "avatar", "archetype", "tagline", "likeCount", "useCount"):
            assert key in vo

    def test_market_filter_archetype(self, user_a):
        page = Api.ok(user_a["api"].get("/api/personas", params={"archetype": "毒舌", "size": 50}))
        assert page["records"], "毒舌原型应有官方演员"
        assert all(p["archetype"] == "毒舌" for p in page["records"])

    def test_market_keyword(self, user_a):
        page = Api.ok(user_a["api"].get("/api/personas", params={"keyword": "Lisa", "size": 50}))
        assert page["records"], "关键词 Lisa 应能搜到毒舌 Lisa"

    def test_market_guest_visible(self, guest):
        """游客可浏览人设市场。"""
        page = Api.ok(guest.get("/api/personas"))
        assert page["records"]


class TestCrud:
    def test_create_update_delete(self, user_a):
        api = user_a["api"]
        body = {
            "name": f"测试演员{uuid4().hex[:4]}",
            "avatar": "🤡",
            "archetype": "杠精",
            "tagline": "测试专用一句话人设",
            "traits": "爱抬杠，见谁怼谁",
            "speakingStyle": "反问句连发",
            "background": "测试背景",
            "taboo": "被说没逻辑",
        }
        created = Api.ok(api.post("/api/personas", body))
        assert created["id"] > 0
        assert created["ownerId"] == user_a["user"]["id"]
        assert created["isOfficial"] == 0

        # 更新
        body["tagline"] = "更新后的一句话人设"
        updated = Api.ok(api.put(f"/api/personas/{created['id']}", body))
        assert updated["tagline"] == "更新后的一句话人设"

        # 删除
        Api.ok(api.delete(f"/api/personas/{created['id']}"))
        _, code, _ = Api.biz_err(api.get(f"/api/personas/{created['id']}"))
        assert code != 0, "删除后应 404"

    def test_create_missing_name(self, user_a):
        r = user_a["api"].post("/api/personas", {"archetype": "毒舌"})
        _, code, _ = Api.biz_err(r)
        assert code != 0, "缺名字应被校验拒绝"

    def test_update_others_persona_forbidden(self, user_a, user_b):
        """B 不应能改 A 的人设。"""
        api_a, api_b = user_a["api"], user_b["api"]
        created = Api.ok(api_a.post("/api/personas", {
            "name": f"维权测试{uuid4().hex[:4]}", "archetype": "毒舌", "tagline": "别人别动我",
        }))
        try:
            r = api_b.put(f"/api/personas/{created['id']}", {
                "name": "被改了", "archetype": "毒舌", "tagline": "被改了",
            })
            _, code, _ = Api.biz_err(r)
            assert code != 0, "越权修改应被拒绝"
        finally:
            Api.ok(api_a.delete(f"/api/personas/{created['id']}"))

    def test_mine_list(self, user_a):
        api = user_a["api"]
        created = Api.ok(api.post("/api/personas", {
            "name": f"我的人设{uuid4().hex[:4]}", "archetype": "社恐", "tagline": "我的列表里要有我",
        }))
        try:
            mine = Api.ok(api.get("/api/personas/mine"))
            assert any(p["id"] == created["id"] for p in mine)
        finally:
            Api.ok(api.delete(f"/api/personas/{created['id']}"))


class TestLike:
    def test_toggle_like(self, user_a, user_b):
        """点赞 → 取消点赞 状态往返。"""
        api = user_a["api"]
        personas = Api.ok(api.get("/api/personas", params={"size": 1}))["records"]
        pid = personas[0]["id"]

        liked = Api.ok(api.post(f"/api/personas/{pid}/like"))
        assert liked is True
        again = Api.ok(api.post(f"/api/personas/{pid}/like"))
        assert again is False

    def test_guest_like_rejected(self, guest):
        personas = Api.ok(guest.get("/api/personas", params={"size": 1}))["records"]
        r = guest.post(f"/api/personas/{personas[0]['id']}/like")
        assert r.status_code == 401


class TestTryChat:
    def test_try_chat_reply(self, user_a):
        """试聊：Mock 模式下返回带 mock 标记的回复。"""
        api = user_a["api"]
        personas = Api.ok(api.get("/api/personas", params={"size": 5}))["records"]
        resp = Api.ok(api.post(f"/api/personas/{personas[0]['id']}/try-chat", {"message": "你行不行啊？"}))
        assert resp["reply"], "试聊必须返回台词"
        assert resp["mock"] is True, "无 Key 部署应走 Mock 并显式标注"

    def test_try_chat_empty_message(self, user_a):
        personas = Api.ok(user_a["api"].get("/api/personas", params={"size": 1}))["records"]
        r = user_a["api"].post(f"/api/personas/{personas[0]['id']}/try-chat", {"message": "  "})
        _, code, _ = Api.biz_err(r)
        assert code != 0
