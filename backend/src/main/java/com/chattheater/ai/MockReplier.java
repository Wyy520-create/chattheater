package com.chattheater.ai;

import com.chattheater.persona.entity.Persona;
import com.chattheater.theater.entity.Message;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 离线演员（Mock）台词生成器：按人设原型的模板库出戏。
 * 占位符：{me}=自己名字 {last}=上一发言者 {q}=上一条消息片段。
 * 模板保证人设味十足，让无 API Key 的部署也能完整体验产品。
 */
public final class MockReplier {

    private static final Map<String, List<String>> BANKS = Map.ofEntries(
            Map.entry("毒舌", List.of(
                    "就这？{q}这种话也说得出口？",
                    "{last}这发言水平，建议回炉重造",
                    "我不是针对谁，我是说在座的各位，发言都有点水",
                    "{q}——好的，我的尴尬癌已经晚期了",
                    "哈？就这智商还来群里发言，勇气可嘉",
                    "让我猜猜，{last}又说不出什么有营养的了",
                    "行吧，{q}，你们开心就好",
                    "这年头什么话都能往群里发了吗",
                    "{last}你先把话说利索了再来反驳我",
                    "笑死，{q}，建议裱起来当反面教材")),
            Map.entry("杠精", List.of(
                    "先问是不是，再问为什么，{q}这个结论哪来的？",
                    "我不完全同意，{q}只说对了一半，另一半全是错的",
                    "有没有一种可能，{last}说的根本不成立？",
                    "不是我说，{q}这个逻辑就站不住脚",
                    "话不能这么说，凡事都有例外，你这就下结论太草率了",
                    "我提个不同意见啊，虽然我知道你们不爱听",
                    "数据呢？证据呢？张口就来？",
                    "{last}你这个观点，我反手就是一个不认同",
                    "勉强同意，但我保留意见，且保留得很有道理",
                    "你说的对，但是我说得更对")),
            Map.entry("慈祥", List.of(
                    "哎哟，孩子们别吵啦，都消消气",
                    "{last}说得也在理，大家都是好孩子",
                    "来来来，吵累了就喝点热水，奶奶给你们煮了绿豆汤",
                    "一家人不说两家话，多大点事呀",
                    "我记得你们小时候可亲密了，怎么长大倒生分了",
                    "行啦行啦，{q}这事翻篇，晚上想吃什么奶奶给你们做",
                    "年轻人火气别那么大，气坏了身子不值当",
                    "要是实在气不过，跟奶奶说，奶奶给你们评理",
                    "哈哈，你们这些孩子，说话一个比一个逗",
                    "都让一步，退一步海阔天空嘛")),
            Map.entry("卷王", List.of(
                    "你们还有空吵？我单词都背完三轮了",
                    "惭愧，今天只学了十四个小时，摆了摆了",
                    "吵归吵，谁把我刚发的学习打卡点个赞？",
                    "{q}？我五分钟前就知道这事了，信息差就是竞争力",
                    "这个话题我写了篇三千字分析，要看私我",
                    "别劝我休息，我的梦想是保研清北+大厂offer双杀",
                    "凌晨四点的图书馆，你们见过吗？我天天见",
                    "说真的，吵架的时间够做完一套真题了",
                    "{last}同学，与其争对错不如争第一",
                    "等我上岸那天，今天吵的每一句都是垫脚石")),
            Map.entry("社恐", List.of(
                    "那个……我能说句话吗（小声）",
                    "呜，怎么又吵起来了，我先躲一会儿……",
                    "{q}……好像、好像也有道理？我不太确定",
                    "大家别吵了行不行，我心跳都加快了",
                    "我我就看看，我不说话……",
                    "能不能别@我，我脸都红了",
                    "其实我想说……算了，还是不说了",
                    "悄悄冒个泡，然后准备火速潜水",
                    "这里好可怕，我还是去角落里待着吧",
                    "呜哇，突然被点名，我先去个洗手间！")),
            Map.entry("吃瓜", List.of(
                    "前排出售瓜子花生矿泉水",
                    "蹲一个后续，这瓜保熟吗？",
                    "好家伙，我直接好家伙，{q}",
                    "别停别停，我搬好小板凳了",
                    "这瓜太大了，我一个人吃不完，@所有人快来",
                    "截图了截图了，这下有证据了",
                    "所以我总结一下：{last}说的和{q}好像都没错？精彩",
                    "来来来，知情人继续爆，我请喝奶茶",
                    "本群今日最佳连续剧，不接受反驳",
                    "哎哟喂，这剧情比电视剧还好看")),
            Map.entry("显眼包", List.of(
                    "看我看我！我先说！我最有发言权！",
                    "家人们谁懂啊，{q}这事必须听我讲",
                    "笑不活了，你们先聊，我表演个才艺助助兴",
                    "刚刚那句谁说的？太有梗了，我要写进我的脱口秀素材",
                    "本群气氛组组长上线，冷场是不可能的",
                    "咳咳，重要的事情说三遍：我我我！",
                    "完了完了，我又要上热搜了（不是）",
                    "让我看看是谁在群里发光？哦，是我自己",
                    "{last}你这句配合我刚刚的动作食用更佳",
                    "都别吵了，听我说——其实我也不知道说啥")),
            Map.entry("暴躁", List.of(
                    "？？{q}？？你再说一遍试试？",
                    "气死我了气死我了，{last}你给我等着",
                    "能不能好好说话？！我血压都上来了！",
                    "这谁干的？！站出来！我保证不打他！",
                    "啊啊啊啊烦死了，都不许吵了！",
                    "你再阴阳怪气一句试试？信不信我当场表演一个暴走",
                    "我今天就是要把话说明白，谁也别拦我",
                    "就这？就这？？就这也能吵起来？？",
                    "我的耐心已经用完了，最后说一遍：{q}",
                    "行，你们聊，我气饱了，饭都省了")),
            Map.entry("阴阳", List.of(
                    "哦~{q}~您说得都对~",
                    "有的人啊，就是了不起呢",
                    "不错不错，这个发言很有个人特色（微笑）",
                    "哇，{last}好厉害哦，鼓掌鼓掌👏",
                    "也不是不行吧，反正您的水平摆在这儿",
                    "嗯嗯，您继续，我就安静听着（才怪）",
                    "这话说得，真是滴水不漏呢（呵呵）",
                    "对对对，{q}，都是我的错行了吧~",
                    "好一个{q}，不愧是您",
                    "没关系，反正我说什么也没人在意（并不是很想说了）")),
            Map.entry("财迷", List.of(
                    "吵什么吵，影响我数钱的心情",
                    "这事谁赔钱？先把账算清楚",
                    "我掐指一算，这波风口能赚三个小目标",
                    "{q}？先别管对错，这里面有商机吗",
                    "谈钱不伤感情，谈感情才伤钱",
                    "各位各位，我发现了新的搞钱路子，就差你们入伙了",
                    "一顿操作猛如虎，一看余额二百五，说的就是我本人",
                    "{last}，昨天借我的五十块，今天该还了吧",
                    "免费吃瓜，那我可就劲头十足了",
                    "别拦我，谁挡我财路我跟谁急")),
            Map.entry("摆烂", List.of(
                    "争什么争，躺平不好吗，{q}关我屁事",
                    "你们吵吧，我先睡了，反正明天也不会变好",
                    "努力不一定成功，但不努力一定很舒服",
                    "{q}？无所谓，反正到头来都是一场空",
                    "别卷了别卷了，沙发才是我最终归宿",
                    "这事我帮不上忙，我连自己都救不了",
                    "老板画饼你们也吃？我连饼渣都不捡",
                    "输赢无所谓，我只想按时下班",
                    "{last}说得都对，但我选择开摆",
                    "人生嘛，混一天是一天")),
            Map.entry("中二", List.of(
                    "哼，凡人的争论，{q}在本座眼里不值一提",
                    "吾之封印即将解开，尔等速速退下",
                    "{q}？这正是命运之轮选中吾的时刻",
                    "愚蠢的{last}啊，见识一下真正的力量吧",
                    "吾名暗夜裁决者，记住这个名字",
                    "这场闹剧，就当是吾觉醒前的消遣",
                    "呵呵，{q}背后隐藏的黑暗，尔等看不见罢了",
                    "本座的人生剧本，没有失败二字",
                    "颤抖吧，吾之劲敌{last}即将见证一切",
                    "命运之门啊，为吾开启吧")),
            Map.entry("茶艺", List.of(
                    "姐姐说得对，不像我，什么都不懂",
                    "{last}哥哥好厉害呀，可以教教我吗",
                    "我不是那个意思啦，姐姐别多想",
                    "呀，{q}这事我可不敢乱说，说了姐姐又要生气",
                    "哥哥姐姐们都好有见识，我跟你们比差远了",
                    "姐姐别误会，我只是担心大家太累",
                    "啊？我这样说姐姐不会生气吧",
                    "都是我不好，害大家吵架了",
                    "{q}好复杂哦，要是有{last}一半聪明就好了",
                    "姐姐你尽情说，我听着就好")),
            Map.entry("老干部", List.of(
                    "咳咳，我说两句啊，{q}这个事情要辩证地看",
                    "年轻人要沉住气，不要一惊一乍",
                    "{q}这事不归我们管，注意边界",
                    "我参加工作三十年，没见过{last}这么莽的",
                    "大局意识要有，牢骚太盛防肠断",
                    "这个事啊，原则上我同意，但是……",
                    "今天就把话撂这儿，谁再搅和试试",
                    "散会！都回去写检查",
                    "{q}反映的问题很典型，值得举一反三",
                    "我跟你们说，当年我们可没这个条件")),
            Map.entry("深情", List.of(
                    "{q}你说是就是吧，反正你说什么我都信",
                    "{last}，你回头看看我，我一直在",
                    "你发的每条消息，我都看了不止一遍",
                    "吵架多伤感情，我们和好行不行",
                    "哪怕你只回我一个嗯，我也开心一整天",
                    "{q}再离谱也没关系，我陪你一起离谱",
                    "我的心意，{last}你真的看不见吗",
                    "别人笑我舔狗，我笑别人不懂爱",
                    "你要的星星我摘不到，但早安晚安一定到",
                    "别生气了，我给你带了奶茶，三分糖去冰")),
            Map.entry("玄学", List.of(
                    "我夜观天象，{q}此事必有蹊跷",
                    "{last}你印堂发黑，最近少说话",
                    "塔罗牌说了，这段缘分三分靠争七分靠等",
                    "水逆！绝对是水逆！{q}都赖水星",
                    "你们信不信，这个群的风水有问题",
                    "我掐指一算，{last}今年命里缺我",
                    "{q}这事，前世因今世果，急不得",
                    "星座运势说了，本周宜吃瓜不宜站队",
                    "都别动！让我摆个阵先",
                    "好事多磨，因果不虚，散了吧散了吧"))
    );

    /** 通用接话（无原型匹配时兜底）。 */
    private static final List<String> GENERIC = List.of(
            "嗯，{q}，有点意思",
            "你们聊你们的，我就路过说一句：这事儿没那么简单",
            "{last}这么一说，我倒想起另一件事",
            "有点意思，让我想想怎么接",
            "真的假的？{q}？",
            "我竟一时不知道先反驳哪一句",
            "哈？刚发生了什么，谁给我补个课",
            "行，这个话题我记下了，改天专门开一场辩论");

    /** 导演指令/事件卡的专属反应。 */
    private static final List<String> DIRECTIVE_REACT = List.of(
            "等等，{d}？这我可就不困了",
            "{d}——好家伙，剧情这就推进了？",
            "先别管{d}，有没有人觉得刚刚那话不对劲",
            "既然{d}，那我必须说两句了",
            "{d}？？信息量有点大，容我缓缓",
            "一说{d}我就来精神了，坐下坐下，都听我说");

    /** 事件卡关键词 → 专属台词。 */
    private static final Map<String, List<String>> EVENT_LINES = Map.of(
            "红包", List.of("我我我我的！谁也别跟我抢！", "红包在哪？！手速就是正义！"),
            "照片", List.of("这照片……谁拍的？构图不行啊", "等等，这照片里好像有故事，放大看了，好家伙"),
            "班主任", List.of("什么？！班主任来了？！我先装睡了", "完了完了，快把聊天记录删了！"),
            "断网", List.of("啊我断网了？刚才发生了什么！", "断网了正好，谁欠我钱还没还的抓紧说"),
            "外卖", List.of("我的外卖到了！谁帮我拿一下，我给好评", "外卖到了都别吵，吃饭最大"),
            "表白", List.of("表白？？？本群今天最大的瓜出现了", "先别表白，先说清楚昨天那事怎么算"));

    private MockReplier() {
    }

    public static String generate(ChatContext ctx) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Persona p = ctx.persona();
        List<String> directives = ctx.directives() == null ? List.of() : ctx.directives();

        // 事件卡优先出专属台词
        for (String d : directives) {
            for (Map.Entry<String, List<String>> e : EVENT_LINES.entrySet()) {
                if (d.contains(e.getKey())) {
                    return fill(pick(e.getValue(), rnd), ctx);
                }
            }
        }
        // 导演指令 → 30% 概率反应模板
        if (!directives.isEmpty() && rnd.nextInt(10) < 3) {
            String d = directives.get(rnd.nextInt(directives.size()));
            String tpl = pick(DIRECTIVE_REACT, rnd).replace("{d}", abbreviate(d, 18));
            return fill(tpl, ctx);
        }
        // 原型模板库 85%，通用兜底 15%
        List<String> bank = BANKS.get(p.getArchetype());
        String tpl = bank != null && rnd.nextInt(100) < 85
                ? pick(bank, rnd)
                : pick(GENERIC, rnd);
        return fill(tpl, ctx);
    }

    /** 人设工坊试聊（单聊语境）。 */
    public static String generateTryTalk(Persona p, String userMessage) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<String> bank = BANKS.get(p.getArchetype());
        String tpl = bank != null ? pick(bank, rnd) : pick(GENERIC, rnd);
        return tpl.replace("{me}", p.getName())
                .replace("{last}", "你")
                .replace("{q}", abbreviate(userMessage, 12));
    }

    private static String fill(String tpl, ChatContext ctx) {
        String last = "各位";
        String quote = "这个话题";
        List<Message> history = ctx.history();
        if (history != null && !history.isEmpty()) {
            Message lastMsg = history.get(history.size() - 1);
            if (!lastMsg.getSenderName().equals(ctx.persona().getName())) {
                last = lastMsg.getSenderName();
            } else if (history.size() > 1) {
                last = history.get(history.size() - 2).getSenderName();
            }
            quote = abbreviate(lastMsg.getContent() == null ? "" : lastMsg.getContent(), 12);
        }
        return tpl.replace("{me}", ctx.persona().getName())
                .replace("{last}", last)
                .replace("{q}", quote);
    }

    private static String pick(List<String> list, ThreadLocalRandom rnd) {
        return list.get(rnd.nextInt(list.size()));
    }

    private static String abbreviate(String s, int max) {
        if (s == null || s.isEmpty()) {
            return "这个";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
