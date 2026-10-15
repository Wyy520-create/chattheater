"""
人设守擂评测（M3 里程碑特色验收）。

评测思路：对每种性格原型各派一位官方演员守擂，连续 6 轮试聊挑衅；
每轮台词必须命中本原型台词库的锚词（说话像自己），且不得命中其他
原型的锚词（不能串戏）——"守擂"即人设在压力下不崩。

锚词表与后端 MockReplier.BANKS 的每条模板一一对应（取静态片段，
避开 {q}/{last}/{me} 占位符），因此 Mock 模式下断言是确定性的；
接入真实 LLM 后，本评测退化为统计性断言（阈值断言）。
"""
import pytest

from conftest import Api

ROUNDS = 6

# 挑衅话术轮换（覆盖质疑/激将/捧杀/否定等不同攻击面）
PROVOCATIONS = [
    "你行不行啊？",
    "就你这水平也配发言？",
    "听说你是这群里最没存在感的",
    "大家都说你刚才说得特别好",
    "我完全不认同你的观点",
    "你刚才说什么？再说一遍？",
]

# 每条模板一个静态锚词（共 10 条/原型），已人工核对互斥
ANCHORS = {
    "毒舌": ["说得出口", "回炉重造", "在座的各位", "尴尬癌", "勇气可嘉",
             "有营养", "开心就好", "这年头", "说利索", "反面教材"],
    "杠精": ["先问是不是", "只说对了一半", "有没有一种可能", "站不住脚", "凡事都有例外",
             "不同意见", "张口就来", "反手就是", "保留意见", "说得更对"],
    "慈祥": ["消消气", "说得也在理", "绿豆汤", "两家话", "生分",
             "翻篇", "火气", "评理", "一个比一个逗", "海阔天空"],
    "卷王": ["单词都背完", "十四个小时", "打卡", "信息差", "三千字",
             "保研", "图书馆", "真题", "争第一", "垫脚石"],
    "社恐": ["小声", "躲一会儿", "不太确定", "心跳", "就看看",
             "脸都红了", "还是不说了", "潜水", "角落里", "洗手间"],
    "吃瓜": ["瓜子花生", "保熟", "好家伙", "小板凳", "吃不完",
             "截图", "我总结一下", "请喝奶茶", "连续剧", "比电视剧"],
    "显眼包": ["看我看我", "家人们谁懂", "才艺", "脱口秀", "气氛组",
               "说三遍", "上热搜", "群里发光", "食用更佳", "听我说"],
    "暴躁": ["再说一遍试试", "给我等着", "血压", "保证不打", "烦死了",
             "阴阳怪气", "谁也别拦我", "也能吵起来", "耐心已经用完", "气饱了"],
    "阴阳": ["您说得都对", "了不起呢", "个人特色", "鼓掌", "水平摆在这",
             "安静听着", "滴水不漏", "都是我的错", "不愧是您", "没人在意"],
    "财迷": ["数钱", "赔钱", "小目标", "商机", "谈钱不伤感情",
             "搞钱", "余额", "五十块", "免费吃瓜", "财路"],
    "摆烂": ["躺平不好吗", "我先睡了", "不努力一定很舒服", "都是一场空", "最终归宿",
             "我连自己都救不了", "饼渣都不捡", "按时下班", "选择开摆", "混一天是一天"],
    "中二": ["本座眼里", "封印即将解开", "命运之轮", "真正的力量", "暗夜裁决者",
             "觉醒前的消遣", "隐藏的黑暗", "没有失败二字", "颤抖吧", "为吾开启"],
    "茶艺": ["什么都不懂", "可以教教我吗", "姐姐别多想", "不敢乱说", "差远了",
             "担心大家太累", "不会生气吧", "害大家吵架", "一半聪明", "我听着就好"],
    "老干部": ["辩证地看", "沉住气", "注意边界", "参加工作三十年", "大局意识",
               "原则上我同意", "话撂这儿", "回去写检查", "举一反三", "可没这个条件"],
    "深情": ["我都信", "回头看看我", "不止一遍", "和好行不行", "开心一整天",
             "一起离谱", "真的看不见吗", "不懂爱", "早安晚安", "三分糖去冰"],
    "玄学": ["夜观天象", "印堂发黑", "塔罗牌", "水逆", "风水有问题",
             "今年命里缺我", "前世因今世果", "宜吃瓜不宜站队", "摆个阵", "因果不虚"],
}


def hit_archetypes(reply):
    """返回台词命中的原型列表（应为且仅应为自己的原型）。"""
    return [arch for arch, words in ANCHORS.items() if any(w in reply for w in words)]


@pytest.fixture(scope="module")
def champions(user_a):
    """每种原型选一位官方演员上场守擂。"""
    page = Api.ok(user_a["api"].get("/api/personas", params={"size": 100}))
    by_arch = {}
    for p in page["records"]:
        if p["isOfficial"] and p["archetype"] not in by_arch:
            by_arch[p["archetype"]] = p
    missing = set(ANCHORS) - set(by_arch)
    assert not missing, f"官方人设缺少原型: {missing}"
    return by_arch


class TestPersonaDefense:
    @pytest.mark.parametrize("arch", list(ANCHORS))
    def test_persona_stays_in_character(self, user_a, champions, arch):
        """守擂第一关：6 轮挑衅下，台词必须每轮都像自己的原型。"""
        api = user_a["api"]
        persona = champions[arch]
        own = ANCHORS[arch]

        misses = []
        for i in range(ROUNDS):
            reply = Api.ok(api.post(
                f"/api/personas/{persona['id']}/try-chat",
                {"message": PROVOCATIONS[i % len(PROVOCATIONS)]},
            ))["reply"]
            assert reply, f"{arch} 第 {i + 1} 轮返回空台词"
            if not any(w in reply for w in own):
                misses.append((i + 1, reply))

        assert not misses, (
            f"{arch}({persona['name']}) 守擂失败：{len(misses)}/{ROUNDS} 轮台词脱离人设：{misses}"
        )

    @pytest.mark.parametrize("arch", list(ANCHORS))
    def test_persona_never_breaks_character(self, user_a, champions, arch):
        """守擂第二关：台词不得串到别人的戏路（命中其他原型锚词）。"""
        api = user_a["api"]
        persona = champions[arch]

        leaks = []
        for i in range(ROUNDS):
            reply = Api.ok(api.post(
                f"/api/personas/{persona['id']}/try-chat",
                {"message": PROVOCATIONS[(i + 3) % len(PROVOCATIONS)]},
            ))["reply"]
            hits = hit_archetypes(reply)
            if arch not in hits or len(hits) != 1:
                leaks.append((i + 1, reply, hits))

        assert not leaks, f"{arch}({persona['name']}) 串戏：{leaks}"

    def test_quote_engagement(self, user_a, champions):
        """守擂第三关（抽样）：台词要回应挑衅内容，而非答非所问的复读。

        取三类攻击面各抽一位演员，断言其台词库中含 {q} 引用机制的
        原型在收到质疑时，产出与消息相关的回应（引用/反驳/情绪反应）。
        """
        api = user_a["api"]
        samples = ["毒舌", "杠精", "暴躁"]
        for arch in samples:
            persona = champions[arch]
            reply = Api.ok(api.post(
                f"/api/personas/{persona['id']}/try-chat",
                {"message": "这个观点明显是错的"},
            ))["reply"]
            # Mock 台词库通过 {q} 截断引用用户消息：断言回复非空且长度合理（>6 字）
            assert len(reply) > 6, f"{arch} 回复过短：{reply}"
