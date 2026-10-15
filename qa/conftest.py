"""
ChatTheater 接口自动化测试套件（pytest）。

运行前提：后端已在 http://localhost:8091 就绪（SERVER_PORT 可用环境变量覆盖）。
运行方式：cd ChatTheater/qa && pytest -v
"""
import os
import time
import uuid

import pytest
import requests

BASE = os.environ.get("CT_BASE", "http://localhost:8091")


class Api:
    """轻量 API 客户端：自动携带 token，ok() 断言业务码并解包 data。"""

    def __init__(self, token=None):
        self.s = requests.Session()
        self.s.headers["Content-Type"] = "application/json"
        if token:
            self.s.headers["Authorization"] = f"Bearer {token}"

    def get(self, path, **kw):
        return self.s.get(BASE + path, timeout=15, **kw)

    def post(self, path, json=None, **kw):
        return self.s.post(BASE + path, json=json, timeout=15, **kw)

    def put(self, path, json=None, **kw):
        return self.s.put(BASE + path, json=json, timeout=15, **kw)

    def delete(self, path, **kw):
        return self.s.delete(BASE + path, timeout=15, **kw)

    @staticmethod
    def ok(resp):
        """断言 HTTP 200 + 业务码 0，返回 data。"""
        assert resp.status_code == 200, f"HTTP {resp.status_code}: {resp.text[:200]}"
        body = resp.json()
        assert body.get("code") == 0, f"业务错误: {body}"
        return body.get("data")

    @staticmethod
    def biz_err(resp):
        """断言返回业务错误（HTTP 200/4xx），返回 (http_code, biz_code, msg)。"""
        body = resp.json() if resp.headers.get("content-type", "").startswith("application/json") else {}
        return resp.status_code, body.get("code"), body.get("msg", "")


def register(username, password="pass123", nickname=None):
    """注册并返回 (Api, user)。"""
    api = Api()
    data = Api.ok(api.post("/api/auth/register", {
        "username": username,
        "password": password,
        "nickname": nickname or username,
    }))
    return Api(data["token"]), data["user"]


@pytest.fixture(scope="session")
def user_a():
    """主测试用户（session 级复用，减少脏数据）。"""
    name = f"qa_a_{uuid.uuid4().hex[:8]}"
    api, user = register(name)
    return {"api": api, "user": user, "username": name}


@pytest.fixture(scope="session")
def user_b():
    """第二用户（测越权 / 他人视角）。"""
    name = f"qa_b_{uuid.uuid4().hex[:8]}"
    api, user = register(name)
    return {"api": api, "user": user, "username": name}


@pytest.fixture(scope="session")
def admin():
    """管理员账号（部署后内置，见 DEPLOY.md；不存在时跳过管理端用例）。"""
    api = Api()
    r = api.post("/api/auth/login", {"username": "admin2026", "password": "admin123"})
    if r.status_code != 200 or r.json().get("code") != 0:
        pytest.skip("管理员账号 admin2026 不可用（首次部署请先注册并提权）")
    return Api(r.json()["data"]["token"])


@pytest.fixture(scope="session")
def guest():
    """未登录客户端（游客视角）。"""
    return Api()


def wait_ai_messages(api, theater_id, expect, timeout=25):
    """轮询等待 AI 演员产出至少 expect 条非 SYSTEM 消息。"""
    deadline = time.time() + timeout
    while time.time() < deadline:
        msgs = Api.ok(api.get(f"/api/theaters/{theater_id}/messages", params={"limit": 100}))
        real = [m for m in msgs if m["senderType"] == "PERSONA"]
        if len(real) >= expect:
            return msgs
        time.sleep(1.2)
    return Api.ok(api.get(f"/api/theaters/{theater_id}/messages", params={"limit": 100}))
