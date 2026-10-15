"""内容安全：敏感词拦截 / 游客权限边界。"""
from conftest import Api

BAD_WORDS = ["赌博", "色情"]  # schema.sql 种子词


class TestModeration:
    def test_user_message_blocked(self, user_a):
        """用户发言命中敏感词：直接拒绝（assertClean 抛业务异常）。"""
        t = Api.ok(user_a["api"].post("/api/theaters", {
            "title": "审核测试", "scenarioId": 1, "personaIds": [1, 2], "visibility": "PRIVATE",
        }))
        r = user_a["api"].post(f"/api/theaters/{t['id']}/messages", {"content": f"我们来聊聊{BAD_WORDS[0]}网站吧"})
        _, code, msg = Api.biz_err(r)
        assert code != 0, "敏感词发言应被拒绝"

    def test_directive_blocked(self, user_a):
        """导演指令同样过审。"""
        t = Api.ok(user_a["api"].post("/api/theaters", {
            "title": "指令审核测试", "scenarioId": 1, "personaIds": [1, 2], "visibility": "PRIVATE",
        }))
        r = user_a["api"].post(f"/api/theaters/{t['id']}/director", {
            "type": "DIRECTIVE", "content": f"都来讨论{BAD_WORDS[1]}话题",
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "敏感词指令应被拒绝"

    def test_persona_create_blocked(self, user_a):
        """人设档案创建同样过审。"""
        r = user_a["api"].post("/api/personas", {
            "name": "坏人设", "archetype": "毒舌", "tagline": f"我是{BAD_WORDS[0]}大王",
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "敏感词人设应被拒绝"


class TestGuestBoundary:
    """游客：只读白名单可用，写操作一律 401。"""

    def test_guest_read_plaza(self, guest):
        stats = Api.ok(guest.get("/api/plaza/stats"))
        assert "liveCount" in stats and "totalMessages" in stats

    def test_guest_read_scenarios(self, guest):
        assert Api.ok(guest.get("/api/scenarios"))

    def test_guest_cannot_post_message(self, guest):
        r = guest.post("/api/theaters/1/messages", {"content": "游客插话"})
        assert r.status_code == 401

    def test_guest_cannot_director(self, guest):
        r = guest.post("/api/theaters/1/director", {"type": "PAUSE"})
        assert r.status_code == 401

    def test_guest_cannot_access_me(self, guest):
        r = guest.get("/api/me/overview")
        assert r.status_code == 401

    def test_guest_cannot_access_admin(self, guest):
        r = guest.get("/api/admin/overview")
        assert r.status_code == 401


class TestAdminGuard:
    def test_normal_user_cannot_admin(self, user_a):
        """普通用户访问运营端应被拒。"""
        r = user_a["api"].get("/api/admin/overview")
        assert r.status_code == 403 or (r.headers.get("content-type", "").startswith("application/json")
                                        and r.json().get("code") not in (0, None))

    def test_admin_overview(self, admin):
        data = Api.ok(admin.get("/api/admin/overview"))
        for key in ("users", "liveTheaters", "endedTheaters", "personas", "messages", "blockedCount"):
            assert key in data

    def test_admin_cost(self, admin):
        data = Api.ok(admin.get("/api/admin/cost"))
        assert "totalCalls" in data and "mockRatio" in data
        assert 0 <= data["mockRatio"] <= 100

    def test_admin_word_crud(self, admin):
        """敏感词增删 + 热生效（删除后原词发言不再被拦）。"""
        added = Api.ok(admin.post("/api/admin/review/words", {"word": "qa专属测试词", "category": "QA"}))
        assert added["id"] > 0
        words = Api.ok(admin.get("/api/admin/review/words", params={"size": 100}))
        assert any(w["id"] == added["id"] for w in words["records"])
        Api.ok(admin.delete(f"/api/admin/review/words/{added['id']}"))

    def test_admin_review_logs(self, admin):
        page = Api.ok(admin.get("/api/admin/review/logs", params={"size": 10}))
        assert "records" in page

    def test_admin_persona_patrol(self, admin):
        page = Api.ok(admin.get("/api/admin/review/personas", params={"size": 10}))
        assert page["records"], "应能巡查全量人设"
        first = page["records"][0]
        assert "status" in first and "isOfficial" in first
