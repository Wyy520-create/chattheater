"""认证模块：注册 / 登录 / 参数校验 / 异常路径。"""
from uuid import uuid4

from conftest import Api


class TestRegister:
    def test_register_ok(self, user_a):
        """注册成功即返回 token 与用户信息。"""
        assert user_a["user"]["id"] > 0
        assert user_a["user"]["username"] == user_a["username"]
        assert user_a["user"]["xp"] == 0
        assert user_a["user"]["role"] == "USER"

    def test_register_duplicate(self, user_a):
        """重复用户名应被拒绝。"""
        api = user_a["api"]
        r = api.post("/api/auth/register", {"username": user_a["username"], "password": "pass123"})
        _, code, _ = Api.biz_err(r)
        assert code != 0

    def test_register_short_password(self):
        """过短密码应被拒绝。"""
        api = Api()
        r = api.post("/api/auth/register", {"username": f"short_{uuid4()}", "password": "123"})
        _, code, _ = Api.biz_err(r)
        assert code != 0


class TestLogin:
    def test_login_ok(self, user_a):
        api = Api()
        data = Api.ok(api.post("/api/auth/login", {
            "username": user_a["username"], "password": "pass123",
        }))
        assert data["token"]
        assert data["user"]["username"] == user_a["username"]

    def test_login_wrong_password(self, user_a):
        api = Api()
        r = api.post("/api/auth/login", {"username": user_a["username"], "password": "wrong-pass"})
        _, code, _ = Api.biz_err(r)
        assert code != 0

    def test_login_unknown_user(self):
        api = Api()
        r = api.post("/api/auth/login", {"username": "no_such_user_xyz", "password": "whatever"})
        _, code, _ = Api.biz_err(r)
        assert code != 0


class TestMe:
    def test_fetch_me(self, user_a):
        me = Api.ok(user_a["api"].get("/api/users/me"))
        assert me["id"] == user_a["user"]["id"]

    def test_me_without_token(self, guest):
        r = guest.get("/api/users/me")
        assert r.status_code == 401
