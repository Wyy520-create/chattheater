"""广场 / 个人中心：卡片流 / 统计 / 名场面 / 成长档案。"""
import pytest

from conftest import Api
from test_theater import create_theater, wait_ai_messages


@pytest.fixture(scope="module")
def seeded_theater(user_a):
    """自建一部直播剧场：本文件不依赖其他测试文件的执行顺序。"""
    api = user_a["api"]
    theater = create_theater(api, title="广场榜单自建", visibility="PUBLIC")
    wait_ai_messages(api, theater["id"], expect=1, timeout=20)
    return theater


@pytest.fixture(scope="module")
def seeded_ended_theater(user_a, seeded_theater):
    """自建一部完结剧场（沿用另一部剧场的演出后杀青）。"""
    api = user_a["api"]
    theater = create_theater(api, title="完结榜单自建", visibility="PUBLIC")
    wait_ai_messages(api, theater["id"], expect=1, timeout=20)
    Api.ok(api.post(f"/api/theaters/{theater['id']}/end", {"reason": "完结榜数据"}))
    return theater


class TestPlaza:
    def test_stats(self, user_a, seeded_theater):
        stats = Api.ok(user_a["api"].get("/api/plaza/stats"))
        assert stats["liveCount"] >= 1, "至少有一场 QA 测试剧场在播"
        assert stats["totalPersonas"] >= 10

    def test_live_list(self, user_a, seeded_theater):
        page = Api.ok(user_a["api"].get("/api/plaza/live", params={"size": 12}))
        assert page["total"] >= 1
        card = page["records"][0]
        for key in ("id", "title", "ownerName", "status", "heat", "castSummary"):
            assert key in card
        assert card["status"] == "LIVE"

    def test_ended_list(self, user_a, seeded_ended_theater):
        page = Api.ok(user_a["api"].get("/api/plaza/ended", params={"size": 12}))
        assert page["total"] >= 1, "应存在完结剧场"
        assert all(c["status"] == "ENDED" for c in page["records"])

    def test_guest_stats(self, guest):
        assert Api.ok(guest.get("/api/plaza/stats"))


class TestHall:
    def test_hall_with_highlight(self, user_a):
        """有高光的完结剧场：名场面馆返回 highlights。"""
        api = user_a["api"]
        # 造一部带点赞台词的完结剧场
        t = create_theater(api, title="名场面馆测试", visibility="PUBLIC")
        wait_ai_messages(api, t["id"], expect=1, timeout=20)
        msgs = Api.ok(api.get(f"/api/theaters/{t['id']}/messages", params={"limit": 50}))
        target = next(m for m in msgs if m["senderType"] == "PERSONA")
        Api.ok(api.post(f"/api/messages/{target['id']}/like"))
        Api.ok(api.post(f"/api/theaters/{t['id']}/end", {"reason": "名场面测试杀青"}))

        hall = Api.ok(api.get(f"/api/plaza/{t['id']}/highlights"))
        assert hall["hasHighlights"] is True
        assert hall["highlights"], "点赞过的台词应进入名场面"
        assert hall["theater"]["id"] == t["id"]
        assert hall["highlights"][0]["likeCount"] >= 1


class TestMe:
    def test_overview(self, user_a):
        """个人总览：XP 体系 + 多维统计字段齐全。"""
        data = Api.ok(user_a["api"].get("/api/me/overview"))
        for key in ("nickname", "xp", "levelName", "nextLevelXp", "levelProgress",
                    "liveTheaters", "endedTheaters", "personaCount", "receivedLikes", "favoriteCount"):
            assert key in data, f"overview 缺字段 {key}"
        assert data["xp"] > 0, "建组/发言后 XP 应增加"

    def test_favorites(self, user_a):
        favorites = Api.ok(user_a["api"].get("/api/me/favorites"))
        assert isinstance(favorites, list)
