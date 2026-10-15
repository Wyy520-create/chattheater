package com.chattheater.moderation;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chattheater.common.BizException;
import com.chattheater.moderation.entity.ReviewLog;
import com.chattheater.moderation.entity.SensitiveWord;
import com.chattheater.moderation.mapper.ReviewLogMapper;
import com.chattheater.moderation.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内容审核：所有用户输入（消息/指令/人设）都走 assertClean；
 * AI 生成内容走 screen（命中则由调用方替换为占位文本）。
 */
@Slf4j
@Service
public class ModerationService {

    private final SensitiveWordMapper wordMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final DfaFilter dfa;

    public ModerationService(SensitiveWordMapper wordMapper, ReviewLogMapper reviewLogMapper, DfaFilter dfa) {
        this.wordMapper = wordMapper;
        this.reviewLogMapper = reviewLogMapper;
        this.dfa = dfa;
    }

    @PostConstruct
    public void init() {
        reload();
        log.info("[Moderation] 敏感词库已加载");
    }

    public void reload() {
        List<String> words = wordMapper.selectList(
                        new UpdateWrapper<SensitiveWord>().eq("status", 1))
                .stream().map(SensitiveWord::getWord).toList();
        dfa.reload(words);
    }

    /** 用户输入硬校验：命中敏感词直接 400 拦截并记 BLOCK 日志。 */
    public void assertClean(String text, String targetType, Long targetId) {
        String hit = dfa.firstHit(text);
        if (hit != null) {
            record(targetType, targetId, hit, "BLOCK", "用户输入被拦截");
            bumpHitCount(hit);
            throw BizException.bad("内容包含违规词，已被拦截");
        }
    }

    /** AI 输出软校验：返回命中词（调用方替换文本），记 FLAG 日志。 */
    public String screen(String text, String targetType, Long targetId) {
        String hit = dfa.firstHit(text);
        if (hit != null) {
            record(targetType, targetId, hit, "FLAG", "AI 输出被替换");
            bumpHitCount(hit);
        }
        return hit;
    }

    private void record(String targetType, Long targetId, String word, String action, String detail) {
        ReviewLog logRow = new ReviewLog();
        logRow.setTargetType(targetType);
        logRow.setTargetId(targetId);
        logRow.setWord(word);
        logRow.setAction(action);
        logRow.setDetail(detail);
        reviewLogMapper.insert(logRow);
    }

    private void bumpHitCount(String word) {
        wordMapper.update(null, new UpdateWrapper<SensitiveWord>()
                .eq("word", word).setSql("hit_count = hit_count + 1"));
    }
}
