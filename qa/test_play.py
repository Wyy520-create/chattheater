"""新玩法：观众投票改剧本（poll）+ 多剧组 PK（pk_match）。

结算依赖截止时间，测试用 pymysql 直连库把 ends_at 拨到过去，
再调读接口触发懒结算（与线上 @Scheduled 兜底等价）。
"""
import os

import pymysql
import pytest

from conftest import Api, register

DB = dict(
    host=os.environ.get("CT_DB_HOST", "127.0.0.1"),
    port=int(os.environ.get("CT_DB_PORT", "3307")),
    user=os.environ.get("CT_DB_USER", "chattheater"),
    password=os.environ.get("CT_DB_PASSWORD", "chattheater123"),
    database=os.environ.get("CT_DB_NAME", "chattheater"),
)


def pick_personas(api, n):
    page = Api.ok(api.get("/api/personas", params={"size": 50, "sort": "hot"}))
    return [p["id"] for p in page["records"][:n]]


def create_theater(api, visibility="PUBLIC", title=None):
    return Api.ok(api.post("/api/theaters", {
        "title": title or "QA 新玩法剧场",
        "scenarioId": 1,
        "personaIds": pick_personas(api, 3),
        "visibility": visibility,
        "directorNote": None,
    }))


def rewind(table, row_id, minutes=1):
    """把某行的 ends_at 拨到过去，触发懒结算。

    默认走 pymysql（dev 栈 3307）；compose 栈的 MySQL 未映射宿主端口，
    设 CT_DB_EXEC=<容器名> 时改借 docker exec 执行 SQL。
    """
    if os.environ.get("CT_DB_EXEC"):
        import subprocess

        sql = (f"UPDATE {table} SET ends_at = DATE_SUB(NOW(), INTERVAL {minutes} MINUTE) "
               f"WHERE id = {row_id}")
        subprocess.run(
            ["docker", "exec", os.environ["CT_DB_EXEC"], "mysql",
             f"-u{DB['user']}", f"-p{DB['password']}", DB["database"], "-e", sql],
            check=True, capture_output=True,
        )
        return
    conn = pymysql.connect(**DB)
    try:
        with conn.cursor() as cur:
            cur.execute(
                f"UPDATE {table} SET ends_at = DATE_SUB(NOW(), INTERVAL %s MINUTE) WHERE id = %s",
                (minutes, row_id),
            )
        conn.commit()
    finally:
        conn.close()


@pytest.fixture(scope="module")
def poll_theater(user_a):
    """投票用剧场（PUBLIC，a 是导演）。"""
    return create_theater(user_a["api"], title="QA 投票剧场")


class TestPollCreate:
    def test_create_poll_ok(self, user_a, poll_theater):
        p = Api.ok(user_a["api"].post(f"/api/theaters/{poll_theater['id']}/polls", {
            "topic": "接下来谁先道歉？",
            "options": ["毒舌姐先低头", "杠精哥服软"],
            "durationSec": 15,
        }))
        assert p["id"] > 0
        assert p["status"] == "OPEN"
        assert p["topic"] == "接下来谁先道歉？"
        assert p["options"] == ["毒舌姐先低头", "杠精哥服软"]
        assert p["counts"] == [0, 0]
        assert p["totalVotes"] == 0
        assert p["endsAt"] > 0

    def test_create_poll_only_director(self, user_b, poll_theater):
        r = user_b["api"].post(f"/api/theaters/{poll_theater['id']}/polls", {
            "topic": "越权投票", "options": ["甲", "乙"], "durationSec": 15,
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "非导演发起投票应被拒"

    def test_create_poll_option_count(self, user_a, poll_theater):
        r = user_a["api"].post(f"/api/theaters/{poll_theater['id']}/polls", {
            "topic": "一个选项", "options": ["只有一个"], "durationSec": 15,
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "少于 2 个选项应被拒"

        r = user_a["api"].post(f"/api/theaters/{poll_theater['id']}/polls", {
            "topic": "五个选项", "options": ["1", "2", "3", "4", "5"], "durationSec": 15,
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "超过 4 个选项应被拒"

    def test_create_poll_duration_bounds(self, user_a, poll_theater):
        r = user_a["api"].post(f"/api/theaters/{poll_theater['id']}/polls", {
            "topic": "太短", "options": ["甲", "乙"], "durationSec": 10,
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "15s 以下应被拒"

        r = user_a["api"].post(f"/api/theaters/{poll_theater['id']}/polls", {
            "topic": "太长", "options": ["甲", "乙"], "durationSec": 121,
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "120s 以上应被拒"

    def test_create_poll_one_open_at_a_time(self, user_a, user_b):
        t = create_theater(user_a["api"], title="QA 单开投票剧场")
        Api.ok(user_a["api"].post(f"/api/theaters/{t['id']}/polls", {
            "topic": "第一场", "options": ["甲", "乙"], "durationSec": 120,
        }))
        r = user_a["api"].post(f"/api/theaters/{t['id']}/polls", {
            "topic": "第二场", "options": ["丙", "丁"], "durationSec": 15,
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "已有 OPEN 投票时不能再发"


class TestPollVote:
    def test_vote_once_each_and_settle(self, user_a, user_b):
        # 独立剧场：poll_theater 上可能还有进行中的投票
        t = create_theater(user_a["api"], title="QA 投票结算剧场")
        api_a, api_b = user_a["api"], user_b["api"]
        p = Api.ok(api_a.post(f"/api/theaters/{t['id']}/polls", {
            "topic": "谁去洗碗？", "options": ["小明去", "小红去", "都不洗"], "durationSec": 15,
        }))
        pid = p["id"]

        v = Api.ok(api_a.post(f"/api/polls/{pid}/vote", {"optionIdx": 0}))
        assert v["counts"] == [1, 0, 0]
        assert v["myVoted"] is True
        assert v["myOption"] == 0

        v = Api.ok(api_b.post(f"/api/polls/{pid}/vote", {"optionIdx": 0}))
        assert v["counts"] == [2, 0, 0]
        assert v["totalVotes"] == 2

        # 一人一票
        r = api_b.post(f"/api/polls/{pid}/vote", {"optionIdx": 1})
        _, code, _ = Api.biz_err(r)
        assert code != 0, "重复投票应被拒"

        # 下标越界
        r = api_a.post(f"/api/polls/{pid}/vote", {"optionIdx": 9})
        _, code, _ = Api.biz_err(r)
        assert code != 0

        # 时间旅行 → 读接口触发懒结算
        rewind("theater_poll", pid)
        final = Api.ok(api_a.get(f"/api/theaters/{t['id']}/polls/active"))
        assert final["status"] == "CLOSED"
        assert final["winnerIdx"] == 0
        assert final["counts"] == [2, 0, 0]
        # 截止后不能投票
        r = api_b.post(f"/api/polls/{pid}/vote", {"optionIdx": 1})
        _, code, _ = Api.biz_err(r)
        assert code != 0

    def test_guest_can_view_active(self, guest, user_a):
        t = create_theater(user_a["api"], title="QA 游客投票剧场")
        Api.ok(user_a["api"].post(f"/api/theaters/{t['id']}/polls", {
            "topic": "游客可见", "options": ["一", "二"], "durationSec": 15,
        }))
        r = guest.get(f"/api/theaters/{t['id']}/polls/active")
        assert r.status_code == 200


@pytest.fixture(scope="module")
def pk_pair(user_a):
    """打擂用的两部 PUBLIC LIVE 剧场。"""
    a = create_theater(user_a["api"], title="QA 甲组")
    b = create_theater(user_a["api"], title="QA 乙组")
    return a, b


class TestPkCreate:
    def test_create_pk_ok(self, user_a, pk_pair):
        a, b = pk_pair
        pk = Api.ok(user_a["api"].post("/api/pk", {
            "title": "QA 巅峰对决",
            "theaterAId": a["id"],
            "theaterBId": b["id"],
            "durationSec": 300,
        }))
        assert pk["id"] > 0
        assert pk["status"] == "OPEN"
        assert pk["title"] == "QA 巅峰对决"
        assert pk["theaterA"]["id"] == a["id"]
        assert pk["theaterB"]["id"] == b["id"]
        assert pk["votesA"] == 0 and pk["votesB"] == 0
        assert pk["scoreA"] == 0 and pk["scoreB"] == 0
        assert pk["endsAt"] > 0

    def test_create_pk_same_theater_twice(self, user_a, pk_pair):
        a, _ = pk_pair
        r = user_a["api"].post("/api/pk", {
            "title": "自己打自己", "theaterAId": a["id"], "theaterBId": a["id"], "durationSec": 300,
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "同一部剧场不能自己打自己"

    def test_create_pk_theater_in_open_pk(self, user_a, pk_pair):
        a, b = pk_pair
        c = create_theater(user_a["api"], title="QA 丙组")
        r = user_a["api"].post("/api/pk", {
            "title": "重复使用剧场", "theaterAId": a["id"], "theaterBId": c["id"], "durationSec": 300,
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "已在 OPEN 擂台中的剧场不能二次上场"

    def test_create_pk_private_rejected(self, user_a):
        t = create_theater(user_a["api"], visibility="PRIVATE", title="QA 私密剧组")
        other = create_theater(user_a["api"], title="QA 对手剧组")
        r = user_a["api"].post("/api/pk", {
            "title": "私密上场", "theaterAId": t["id"], "theaterBId": other["id"], "durationSec": 300,
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "私密剧场不能上擂台"

    def test_create_pk_ended_rejected(self, user_a):
        t = create_theater(user_a["api"], title="QA 已杀青剧组")
        Api.ok(user_a["api"].post(f"/api/theaters/{t['id']}/end", {"reason": "QA 杀青"}))
        other = create_theater(user_a["api"], title="QA 对手剧组2")
        r = user_a["api"].post("/api/pk", {
            "title": "杀青也上场", "theaterAId": t["id"], "theaterBId": other["id"], "durationSec": 300,
        })
        _, code, _ = Api.biz_err(r)
        assert code != 0, "已杀青剧场不能上擂台"


class TestPkVote:
    def test_cheer_and_settle(self, user_a, user_b):
        # 独立剧场对：pk_pair 已挂在别的 OPEN 擂台上
        a = create_theater(user_a["api"], title="QA 助威甲组")
        b = create_theater(user_a["api"], title="QA 助威乙组")
        api_a, api_b = user_a["api"], user_b["api"]
        api_c, _ = register(f"qa_c_{__import__('uuid').uuid4().hex[:8]}")
        pk = Api.ok(api_a.post("/api/pk", {
            "title": "QA 助威结算", "theaterAId": a["id"], "theaterBId": b["id"], "durationSec": 300,
        }))
        pid = pk["id"]

        # A 方两票（a、c）、B 方一票（b）
        fresh = Api.ok(api_a.post(f"/api/pk/{pid}/vote", {"side": "A"}))
        assert fresh["votesA"] == 1
        assert fresh["mySide"] == "A"
        fresh = Api.ok(api_b.post(f"/api/pk/{pid}/vote", {"side": "B"}))
        assert fresh["votesB"] == 1
        fresh = Api.ok(api_c.post(f"/api/pk/{pid}/vote", {"side": "A"}))
        assert fresh["votesA"] == 2

        # 一人一票
        r = api_a.post(f"/api/pk/{pid}/vote", {"side": "B"})
        _, code, _ = Api.biz_err(r)
        assert code != 0, "重复助威应被拒"

        # 非法方
        r = api_a.post(f"/api/pk/{pid}/vote", {"side": "C"})
        _, code, _ = Api.biz_err(r)
        assert code != 0

        # 时间旅行 → detail 触发懒结算：得分 = 热度增量 + 票×3
        rewind("pk_match", pid)
        final = Api.ok(api_a.get(f"/api/pk/{pid}"))
        assert final["status"] == "CLOSED"
        assert final["winner"] == "A"
        assert final["votesA"] == 2 and final["votesB"] == 1
        assert final["scoreA"] >= 6
        assert final["scoreB"] >= 3
        assert final["scoreA"] > final["scoreB"]

        # 结算后不能助威
        r = api_a.post(f"/api/pk/{pid}/vote", {"side": "B"})
        _, code, _ = Api.biz_err(r)
        assert code != 0

    def test_guest_can_view_pk(self, guest, user_a):
        a = create_theater(user_a["api"], title="QA 围观甲组")
        b = create_theater(user_a["api"], title="QA 围观乙组")
        Api.ok(user_a["api"].post("/api/pk", {
            "title": "游客围观擂台", "theaterAId": a["id"], "theaterBId": b["id"], "durationSec": 300,
        }))
        r = guest.get("/api/pk/list")
        assert r.status_code == 200
        body = r.json()
        assert body["code"] == 0
        assert any(x["title"] == "游客围观擂台" for x in body["data"])
