package com.chattheater.user.dto;

import java.util.List;

/**
 * 导演成长等级：群演 → 场记 → 副导演 → 导演 → 金牌导演。
 */
public final class LevelCalculator {

    /** 等级门槛：[minXp, maxXp) 左闭右开。 */
    private record Tier(int min, int max, String name) {
    }

    private static final List<Tier> TIERS = List.of(
            new Tier(0, 50, "群演"),
            new Tier(50, 200, "场记"),
            new Tier(200, 500, "副导演"),
            new Tier(500, 1200, "导演"),
            new Tier(1200, Integer.MAX_VALUE, "金牌导演")
    );

    private LevelCalculator() {
    }

    public record Level(String name, int minXp, int nextXp, int progress) {
    }

    public static Level of(int xp) {
        for (Tier t : TIERS) {
            if (xp >= t.min() && xp < t.max()) {
                int span = t.max() == Integer.MAX_VALUE ? 1 : t.max() - t.min();
                int progress = t.max() == Integer.MAX_VALUE ? 100 : (xp - t.min()) * 100 / span;
                return new Level(t.name(), t.min(), t.max(), progress);
            }
        }
        return new Level("群演", 0, 50, 0);
    }
}
