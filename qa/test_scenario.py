"""场景模块：官方剧本列表 / 详情。"""
from conftest import Api

class TestScenario:
    def test_list(self, user_a):
        scenarios = Api.ok(user_a["api"].get("/api/scenarios"))
        assert len(scenarios) >= 6, "官方种子应有 6 个场景"
        names = {s["name"] for s in scenarios}
        assert any("宿舍" in n for n in names), "应包含宿舍场景"
        for s in scenarios:
            assert s["backdrop"], f"场景 {s['name']} 缺舞台描述"
            assert s["suggestedCast"] >= 2

    def test_detail(self, user_a):
        scenarios = Api.ok(user_a["api"].get("/api/scenarios"))
        one = Api.ok(user_a["api"].get(f"/api/scenarios/{scenarios[0]['id']}"))
        assert one["id"] == scenarios[0]["id"]

    def test_detail_not_found(self, user_a):
        _, code, _ = Api.biz_err(user_a["api"].get("/api/scenarios/999999"))
        assert code != 0

    def test_guest_visible(self, guest):
        scenarios = Api.ok(guest.get("/api/scenarios"))
        assert scenarios
