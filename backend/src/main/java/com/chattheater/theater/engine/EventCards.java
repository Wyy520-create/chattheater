package com.chattheater.theater.engine;

import java.util.List;

/**
 * 预置事件卡：一键扔进群里的戏剧性事件。
 * key 与 MockReplier.EVENT_LINES 的关键词对齐——Mock 演员会出专属反应台词。
 */
public final class EventCards {

    public record Card(String key, String icon, String title, String text) {
        /** 群里的 SYSTEM 气泡文案。 */
        public String announce() {
            return icon + "【" + title + "】" + text;
        }
    }

    public static final List<Card> ALL = List.of(
            new Card("红包", "🧧", "红包来袭", "导演突然往群里扔了个大红包，手快有手慢无！"),
            new Card("照片", "📷", "照片风波", "有人在群里发了张神秘照片，所有人开始放大找细节……"),
            new Card("班主任", "🚨", "班主任查岗", "班主任突然出现在门口，群里瞬间安静如鸡……"),
            new Card("断网", "🔌", "断网了", "网络突然波动，有人的消息发了一半卡住了！"),
            new Card("外卖", "🍱", "外卖到了", "外卖小哥在楼下喊了一嗓子，谁点的赶紧去拿！"),
            new Card("表白", "💌", "匿名表白", "群里突然出现一条匿名表白，矛头直指某人……"));

    /** 按 key 找卡（找不到返回 null，由调用方兜底）。 */
    public static Card byKey(String key) {
        return ALL.stream().filter(c -> c.key().equals(key)).findFirst().orElse(null);
    }

    private EventCards() {
    }
}
