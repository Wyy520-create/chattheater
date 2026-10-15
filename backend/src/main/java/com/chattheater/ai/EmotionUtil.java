package com.chattheater.ai;

import java.util.Map;

/**
 * 情绪启发式标注：关键词/标点/原型偏置 → 7 类情绪。
 * 有意不用模型分类：零成本、可解释、行为可预期。
 */
public final class EmotionUtil {

    public static final Map<String, String> EMOTION_LABELS = Map.of(
            "joy", "开心",
            "angry", "生气",
            "sad", "难过",
            "shock", "震惊",
            "jealousy", "酸了",
            "pride", "得意",
            "neutral", "平静");

    private static final Map<String, String> KEYWORDS = Map.ofEntries(
            Map.entry("joy", "哈哈|嘿嘿|笑死|好家伙|爱了|绝了|太好|开心|😄|😂|🤣|🎉|❤️"),
            Map.entry("angry", "气死|烦死|滚|闭嘴|气人|血压|暴走|？？|💢|😡|🔥"),
            Map.entry("sad", "呜|难过|想哭|伤心|眼泪|😭|😢"),
            Map.entry("shock", "什么？！|天呐|离谱|离大谱|震惊|卧槽|？！|😱|😲"),
            Map.entry("jealousy", "凭什么|酸了|羡慕|嫉妒|我也想要"),
            Map.entry("pride", "不愧是我|稳了|赢了|第一名|就是这么|帅吧|😎"));

    /** 各原型的默认情绪偏置（关键词未命中时）。 */
    private static final Map<String, String> ARCHETYPE_BIAS = Map.of(
            "暴躁", "angry",
            "阴阳", "jealousy",
            "卷王", "pride",
            "社恐", "sad",
            "吃瓜", "shock",
            "显眼包", "joy");

    private EmotionUtil() {
    }

    public static String classify(String text, String archetype) {
        if (text == null || text.isEmpty()) {
            return "neutral";
        }
        for (Map.Entry<String, String> e : KEYWORDS.entrySet()) {
            for (String kw : e.getValue().split("\\|")) {
                if (text.contains(kw)) {
                    return e.getKey();
                }
            }
        }
        return ARCHETYPE_BIAS.getOrDefault(archetype, "neutral");
    }

    /** 情绪对热度的加成。 */
    public static int intensityBonus(String emotion) {
        return switch (emotion) {
            case "angry", "shock" -> 3;
            case "joy", "jealousy", "pride" -> 2;
            case "sad" -> 1;
            default -> 0;
        };
    }
}
