package com.chattheater.moderation;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DFA（前缀树）敏感词过滤器。
 * 重建采用"构建新树后整体替换引用"的写法，保证 reload 期间读操作始终针对完整树，无并发撕裂。
 */
@Component
public class DfaFilter {

    private static class Node {
        Map<Character, Node> children = new HashMap<>();
        boolean end;
    }

    private volatile Node root = new Node();

    public synchronized void reload(List<String> words) {
        Node newRoot = new Node();
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            Node cur = newRoot;
            for (char c : word.toCharArray()) {
                cur = cur.children.computeIfAbsent(c, k -> new Node());
            }
            cur.end = true;
        }
        this.root = newRoot;
    }

    /** 返回文本中命中的第一个敏感词，未命中返回 null。 */
    public String firstHit(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Node cur = root;
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Node next = cur.children.get(c);
            if (next == null) {
                // 回退：从 start+1 重新开始（经典 DFA 扫描回溯）
                if (start >= 0) {
                    i = start;
                }
                start = -1;
                cur = root;
                continue;
            }
            if (start < 0) {
                start = i;
            }
            cur = next;
            if (cur.end) {
                return text.substring(start, i + 1);
            }
        }
        return null;
    }
}
