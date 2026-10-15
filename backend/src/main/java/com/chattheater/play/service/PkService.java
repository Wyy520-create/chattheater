package com.chattheater.play.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chattheater.common.BizException;
import com.chattheater.moderation.ModerationService;
import com.chattheater.play.dto.PlayDtos.PkCreateReq;
import com.chattheater.play.dto.PlayDtos.PkVO;
import com.chattheater.play.entity.PkMatch;
import com.chattheater.play.entity.PkVote;
import com.chattheater.play.mapper.PkMatchMapper;
import com.chattheater.play.mapper.PkVoteMapper;
import com.chattheater.theater.dto.TheaterDtos.TheaterVO;
import com.chattheater.theater.entity.TheaterGroup;
import com.chattheater.theater.mapper.TheaterGroupMapper;
import com.chattheater.theater.service.TheaterService;
import com.chattheater.theater.sse.SseHub;
import com.chattheater.user.entity.User;
import com.chattheater.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 多剧组 PK：两部公开直播的剧场同场打擂，
 * 得分 = 擂台期热度增量 + 观众助威票数×3，平局记 T。
 *
 * PK 跨两个剧场，SSE 仍按单剧场房间广播，因此 PK 详情走轮询；
 * 结算时分别往两部剧场发系统广播。懒结算 + 定时任务兜底。
 */
@Slf4j
@Service
public class PkService {

    private static final int MAX_TITLE_LEN = 60;
    private static final int MIN_DURATION_SEC = 300;
    private static final int MAX_DURATION_SEC = 3600;
    private static final int DEFAULT_DURATION_SEC = 900;
    private static final int WIN_HEAT_BONUS = 20;
    private static final int TIE_HEAT_BONUS = 10;
    private static final int WIN_XP_BONUS = 10;

    private final PkMatchMapper pkMapper;
    private final PkVoteMapper voteMapper;
    private final TheaterGroupMapper groupMapper;
    private final TheaterService theaterService;
    private final UserService userService;
    private final ModerationService moderation;
    private final SseHub sseHub;

    public PkService(PkMatchMapper pkMapper, PkVoteMapper voteMapper,
                     TheaterGroupMapper groupMapper, TheaterService theaterService,
                     UserService userService, ModerationService moderation, SseHub sseHub) {
        this.pkMapper = pkMapper;
        this.voteMapper = voteMapper;
        this.groupMapper = groupMapper;
        this.theaterService = theaterService;
        this.userService = userService;
        this.moderation = moderation;
        this.sseHub = sseHub;
    }

    // ==================== 发起擂台 ====================

    public PkVO create(User me, PkCreateReq req) {
        if (req.getTheaterAId() == null || req.getTheaterBId() == null) {
            throw BizException.bad("请选出打擂的两部剧场");
        }
        if (req.getTheaterAId().equals(req.getTheaterBId())) {
            throw BizException.bad("同一部剧场不能自己打自己");
        }
        TheaterGroup a = getLivePublicOrThrow(req.getTheaterAId());
        TheaterGroup b = getLivePublicOrThrow(req.getTheaterBId());
        Long inOpenPk = pkMapper.selectCount(new QueryWrapper<PkMatch>()
                .eq("status", "OPEN")
                .and(w -> w.eq("theater_a_id", a.getId()).or().eq("theater_b_id", a.getId())
                        .or().eq("theater_a_id", b.getId()).or().eq("theater_b_id", b.getId())));
        if (inOpenPk > 0) {
            throw BizException.bad("有剧场已经在别的擂台上，打完这场再约");
        }
        int duration = req.getDurationSec() == null ? DEFAULT_DURATION_SEC : req.getDurationSec();
        if (duration < MIN_DURATION_SEC || duration > MAX_DURATION_SEC) {
            throw BizException.bad("擂台时长 " + (MIN_DURATION_SEC / 60) + "~" + (MAX_DURATION_SEC / 60) + " 分钟");
        }
        String title = req.getTitle() == null || req.getTitle().isBlank()
                ? "《" + a.getTitle() + "》VS《" + b.getTitle() + "》"
                : req.getTitle().trim();
        if (title.length() > MAX_TITLE_LEN) {
            throw BizException.bad("擂台标题最长 " + MAX_TITLE_LEN + " 字");
        }
        moderation.assertClean(title, "PK", me.getId());

        PkMatch pk = new PkMatch();
        pk.setTitle(title);
        pk.setOwnerId(me.getId());
        pk.setTheaterAId(a.getId());
        pk.setTheaterBId(b.getId());
        pk.setHeatAStart(a.getHeat());
        pk.setHeatBStart(b.getHeat());
        pk.setVotesA(0);
        pk.setVotesB(0);
        pk.setStatus("OPEN");
        pk.setEndsAt(LocalDateTime.now().plusSeconds(duration));
        pkMapper.insert(pk);

        String banner = "⚔️ 剧组 PK 开打：" + a.getTitle() + " VS " + b.getTitle() +
                "，观众快来给支持的剧组助威！";
        sseHub.system(a.getId(), banner);
        sseHub.system(b.getId(), banner);
        log.info("[PK] 用户 {} 发起擂台 {}「{}」：{} vs {}",
                me.getId(), pk.getId(), title, a.getId(), b.getId());
        return voOf(pk, me);
    }

    // ==================== 查询（懒结算） ====================

    public PkVO detail(Long pkId, User viewer) {
        PkMatch pk = pkMapper.selectById(pkId);
        if (pk == null) {
            throw BizException.notFound("擂台不存在");
        }
        if ("OPEN".equals(pk.getStatus()) && pk.getEndsAt().isBefore(LocalDateTime.now())) {
            settle(pk);
            pk = pkMapper.selectById(pkId);
        }
        return voOf(pk, viewer);
    }

    /** 最近 20 场擂台，OPEN 优先、新的在前。 */
    public List<PkVO> list(User viewer) {
        List<PkMatch> all = pkMapper.selectList(new QueryWrapper<PkMatch>()
                .orderByDesc("status").orderByDesc("id").last("LIMIT 20"));
        return all.stream().map(pk -> {
            if ("OPEN".equals(pk.getStatus()) && pk.getEndsAt().isBefore(LocalDateTime.now())) {
                settle(pk);
                pk = pkMapper.selectById(pk.getId());
            }
            return voOf(pk, viewer);
        }).toList();
    }

    // ==================== 助威投票 ====================

    public PkVO vote(User me, Long pkId, String side) {
        PkMatch pk = pkMapper.selectById(pkId);
        if (pk == null) {
            throw BizException.notFound("擂台不存在");
        }
        if ("OPEN".equals(pk.getStatus()) && pk.getEndsAt().isBefore(LocalDateTime.now())) {
            settle(pk);
            throw BizException.bad("擂台刚分出胜负，下次手快点");
        }
        if (!"OPEN".equals(pk.getStatus())) {
            throw BizException.bad("擂台已结束");
        }
        String s = side == null ? "" : side.toUpperCase();
        if (!"A".equals(s) && !"B".equals(s)) {
            throw BizException.bad("请选择助威对象：A 或 B");
        }
        PkVote vote = new PkVote();
        vote.setPkId(pkId);
        vote.setUserId(me.getId());
        vote.setSide(s);
        try {
            voteMapper.insert(vote);
        } catch (DuplicateKeyException e) {
            throw BizException.bad("一人一票，你已经助威过了");
        }
        pkMapper.update(null, new UpdateWrapper<PkMatch>()
                .eq("id", pkId)
                .setSql(("A".equals(s) ? "votes_a" : "votes_b") + " = " + ("A".equals(s) ? "votes_a" : "votes_b") + " + 1"));
        userService.addXp(me.getId(), 1);
        log.info("[PK] 擂台 {} 新增 {} 方一票（用户 {}）", pkId, s, me.getId());
        return detail(pkId, me);
    }

    // ==================== 结算 ====================

    /**
     * 结算：得分 = 热度增量 + 票数×3；胜者剧场热度 +20（封顶 100）、导演 XP +10，
     * 平局两部各 +10 热度；向两部剧场广播战果。
     */
    private void settle(PkMatch pk) {
        TheaterGroup a = groupMapper.selectById(pk.getTheaterAId());
        TheaterGroup b = groupMapper.selectById(pk.getTheaterBId());
        int heatA = a == null ? pk.getHeatAStart() : a.getHeat();
        int heatB = b == null ? pk.getHeatBStart() : b.getHeat();
        int scoreA = Math.max(0, heatA - pk.getHeatAStart()) + pk.getVotesA() * 3;
        int scoreB = Math.max(0, heatB - pk.getHeatBStart()) + pk.getVotesB() * 3;
        String winner = scoreA == scoreB ? "T" : (scoreA > scoreB ? "A" : "B");

        pkMapper.update(null, new UpdateWrapper<PkMatch>()
                .eq("id", pk.getId()).set("status", "CLOSED").set("winner", winner));

        int heatBonus = "T".equals(winner) ? TIE_HEAT_BONUS : WIN_HEAT_BONUS;
        bumpHeat(pk.getTheaterAId(), "T".equals(winner) || "A".equals(winner) ? heatBonus : 0);
        bumpHeat(pk.getTheaterBId(), "T".equals(winner) || "B".equals(winner) ? heatBonus : 0);
        // 胜方剧场的导演拿 XP；平局双方导演都有鼓励奖。
        Long bonusUser = null;
        if ("A".equals(winner)) {
            bonusUser = a == null ? null : a.getOwnerId();
        } else if ("B".equals(winner)) {
            bonusUser = b == null ? null : b.getOwnerId();
        } else {
            if (a != null && a.getOwnerId() != null) {
                userService.addXp(a.getOwnerId(), WIN_XP_BONUS / 2);
            }
            if (b != null && b.getOwnerId() != null) {
                userService.addXp(b.getOwnerId(), WIN_XP_BONUS / 2);
            }
        }
        if (bonusUser != null) {
            userService.addXp(bonusUser, WIN_XP_BONUS);
        }

        String result = switch (winner) {
            case "A" -> "🏆 剧组 PK 结果：" + theaterTitle(pk.getTheaterAId()) + " 胜出（" +
                    scoreA + " : " + scoreB + "）！";
            case "B" -> "🏆 剧组 PK 结果：" + theaterTitle(pk.getTheaterBId()) + " 胜出（" +
                    scoreB + " : " + scoreA + "）！";
            default -> "🤝 剧组 PK 结果：势均力敌（" + scoreA + " : " + scoreB + "），双剧同辉！";
        };
        sseHub.system(pk.getTheaterAId(), result);
        sseHub.system(pk.getTheaterBId(), result);
        log.info("[PK] 擂台 {} 结算：{}（{}:{}）", pk.getId(), winner, scoreA, scoreB);
    }

    /** 定时兜底：每 10 秒扫一遍过期的 OPEN 擂台。 */
    @Scheduled(fixedDelay = 10_000)
    public void settleOverdue() {
        List<PkMatch> overdue = pkMapper.selectList(new QueryWrapper<PkMatch>()
                .eq("status", "OPEN").lt("ends_at", LocalDateTime.now()));
        for (PkMatch pk : overdue) {
            try {
                settle(pk);
            } catch (Exception e) {
                log.warn("[PK] 定时结算擂台 {} 失败：{}", pk.getId(), e.getMessage());
            }
        }
    }

    // ==================== 私有辅助 ====================

    private PkVO voOf(PkMatch pk, User viewer) {
        TheaterGroup a = groupMapper.selectById(pk.getTheaterAId());
        TheaterGroup b = groupMapper.selectById(pk.getTheaterBId());
        TheaterVO voA = a == null ? null : theaterService.detail(pk.getTheaterAId(), null);
        TheaterVO voB = b == null ? null : theaterService.detail(pk.getTheaterBId(), null);
        int heatA = a == null ? pk.getHeatAStart() : a.getHeat();
        int heatB = b == null ? pk.getHeatBStart() : b.getHeat();
        int deltaA = Math.max(0, heatA - pk.getHeatAStart());
        int deltaB = Math.max(0, heatB - pk.getHeatBStart());
        int scoreA = deltaA + pk.getVotesA() * 3;
        int scoreB = deltaB + pk.getVotesB() * 3;
        boolean myVoted = false;
        String mySide = null;
        if (viewer != null) {
            PkVote mine = voteMapper.selectOne(new QueryWrapper<PkVote>()
                    .eq("pk_id", pk.getId()).eq("user_id", viewer.getId()).last("LIMIT 1"));
            if (mine != null) {
                myVoted = true;
                mySide = mine.getSide();
            }
        }
        return PkVO.of(pk, voA, voB, scoreA, scoreB, myVoted, mySide);
    }

    private TheaterGroup getLivePublicOrThrow(Long groupId) {
        TheaterGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BizException.notFound("剧场 " + groupId + " 不存在");
        }
        if (!"LIVE".equals(group.getStatus())) {
            throw BizException.bad("《" + group.getTitle() + "》还没在直播，上不了擂台");
        }
        if (!"PUBLIC".equals(group.getVisibility())) {
            throw BizException.bad("私密剧场不能上擂台，先改成公开");
        }
        return group;
    }

    private void bumpHeat(Long groupId, int bonus) {
        if (bonus <= 0) {
            return;
        }
        groupMapper.update(null, new UpdateWrapper<TheaterGroup>()
                .eq("id", groupId).setSql("heat = LEAST(100, heat + " + bonus + ")"));
    }

    private String theaterTitle(Long groupId) {
        TheaterGroup g = groupMapper.selectById(groupId);
        return g == null ? "神秘剧组" : g.getTitle();
    }
}
