package com.chattheater.theater.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chattheater.common.BizException;
import com.chattheater.moderation.ModerationService;
import com.chattheater.persona.entity.Persona;
import com.chattheater.persona.mapper.PersonaMapper;
import com.chattheater.persona.service.PersonaService;
import com.chattheater.scenario.entity.Scenario;
import com.chattheater.scenario.mapper.ScenarioMapper;
import com.chattheater.theater.dto.TheaterDtos.CreateReq;
import com.chattheater.theater.dto.TheaterDtos.DirectorReq;
import com.chattheater.theater.dto.TheaterDtos.MemberVO;
import com.chattheater.theater.dto.TheaterDtos.MessageVO;
import com.chattheater.theater.dto.TheaterDtos.TheaterVO;
import com.chattheater.theater.entity.DirectorEvent;
import com.chattheater.theater.entity.Favorite;
import com.chattheater.theater.entity.GroupLike;
import com.chattheater.theater.entity.GroupMember;
import com.chattheater.theater.entity.Message;
import com.chattheater.theater.entity.MessageLike;
import com.chattheater.theater.entity.TheaterGroup;
import com.chattheater.theater.engine.ChatEngine;
import com.chattheater.theater.engine.EventCards;
import com.chattheater.theater.mapper.DirectorEventMapper;
import com.chattheater.theater.mapper.FavoriteMapper;
import com.chattheater.theater.mapper.GroupLikeMapper;
import com.chattheater.theater.mapper.GroupMemberMapper;
import com.chattheater.theater.mapper.MessageLikeMapper;
import com.chattheater.theater.mapper.MessageMapper;
import com.chattheater.theater.mapper.TheaterGroupMapper;
import com.chattheater.theater.sse.SseHub;
import com.chattheater.user.entity.User;
import com.chattheater.user.mapper.UserMapper;
import com.chattheater.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 剧场业务：建组开拍 → 发言/导演操控 → 完结谢幕 → 点赞收藏。
 *
 * 越权矩阵：导演台操作与完结仅 owner（403）；PUBLIC 剧场任意登录用户可发言；
 * PRIVATE 仅 owner；围观（详情/历史/SSE）对 PUBLIC 开放到游客。
 */
@Slf4j
@Service
public class TheaterService {

    private static final int MAX_TITLE_LEN = 60;
    private static final int MAX_MSG_LEN = 500;
    private static final int MAX_DIRECTIVE_LEN = 200;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int HIGHLIGHT_TOP = 3;

    private final TheaterGroupMapper groupMapper;
    private final GroupMemberMapper memberMapper;
    private final MessageMapper messageMapper;
    private final MessageLikeMapper messageLikeMapper;
    private final GroupLikeMapper groupLikeMapper;
    private final FavoriteMapper favoriteMapper;
    private final DirectorEventMapper eventMapper;
    private final PersonaMapper personaMapper;
    private final ScenarioMapper scenarioMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final PersonaService personaService;
    private final ModerationService moderation;
    private final ChatEngine engine;
    private final SseHub sseHub;

    public TheaterService(TheaterGroupMapper groupMapper, GroupMemberMapper memberMapper,
                           MessageMapper messageMapper, MessageLikeMapper messageLikeMapper,
                           GroupLikeMapper groupLikeMapper, FavoriteMapper favoriteMapper,
                           DirectorEventMapper eventMapper, PersonaMapper personaMapper,
                           ScenarioMapper scenarioMapper, UserMapper userMapper,
                           UserService userService, PersonaService personaService,
                           ModerationService moderation, ChatEngine engine, SseHub sseHub) {
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.messageMapper = messageMapper;
        this.messageLikeMapper = messageLikeMapper;
        this.groupLikeMapper = groupLikeMapper;
        this.favoriteMapper = favoriteMapper;
        this.eventMapper = eventMapper;
        this.personaMapper = personaMapper;
        this.scenarioMapper = scenarioMapper;
        this.userMapper = userMapper;
        this.userService = userService;
        this.personaService = personaService;
        this.moderation = moderation;
        this.engine = engine;
        this.sseHub = sseHub;
    }

    // ==================== 建组开拍 ====================

    @Transactional
    public TheaterVO create(User me, CreateReq req) {
        // 演员去重后 2~6 人
        Set<Long> personaIds = new LinkedHashSet<>(req.getPersonaIds() == null
                ? List.of() : req.getPersonaIds());
        if (personaIds.size() < 2 || personaIds.size() > 6) {
            throw BizException.bad("剧组需要 2~6 位演员（去重后）");
        }
        Scenario scenario = scenarioMapper.selectById(req.getScenarioId());
        if (scenario == null) {
            throw BizException.notFound("场景剧本不存在");
        }
        List<Persona> cast = personaIds.stream().map(personaService::getOrThrow).toList();

        String title = req.getTitle() == null || req.getTitle().isBlank()
                ? scenario.getName() : req.getTitle().trim();
        if (title.length() > MAX_TITLE_LEN) {
            throw BizException.bad("标题最长 " + MAX_TITLE_LEN + " 字");
        }
        String note = req.getDirectorNote() == null ? null : req.getDirectorNote().trim();
        moderation.assertClean(title + " " + (note == null ? "" : note), "GROUP", me.getId());

        TheaterGroup group = new TheaterGroup();
        group.setOwnerId(me.getId());
        group.setTitle(title);
        group.setScenarioId(scenario.getId());
        group.setScenarioSnapshot(scenario.getBackdrop());
        group.setDirectorNote(note);
        group.setStatus("LIVE");
        group.setVisibility("PRIVATE".equalsIgnoreCase(req.getVisibility()) ? "PRIVATE" : "PUBLIC");
        group.setHeat(0);
        group.setMessageCount(0);
        group.setLikeCount(0);
        group.setViewCount(0);
        groupMapper.insert(group);

        for (Long pid : personaIds) {
            GroupMember gm = new GroupMember();
            gm.setGroupId(group.getId());
            gm.setPersonaId(pid);
            gm.setActivity(50);
            gm.setIsMuted(0);
            memberMapper.insert(gm);
        }

        // 采用计数 + 场景使用计数 + XP（开拍+10，人设被采用+3 给非官方作者）
        personaService.markAdopted(List.copyOf(personaIds));
        scenarioMapper.update(null, new UpdateWrapper<Scenario>()
                .eq("id", scenario.getId()).setSql("use_count = use_count + 1"));
        userService.addXp(me.getId(), 10);
        for (Persona p : cast) {
            if (p.getOwnerId() != null) {
                userService.addXp(p.getOwnerId(), 3);
            }
        }

        // 开场系统消息 + 默认导演指令 + 点火开拍。
        // 注意：引擎跑在另一条线程/连接上，必须等建组事务提交后才能点火，
        // 否则 runLoop 读不到未提交的 group 记录会静默退出。
        saveSystem(group.getId(), "🎬 开演《" + title + "》！舞台：" +
                abbreviate(scenario.getBackdrop(), 80));
        log.info("[Theater] 用户 {} 开拍剧场 {}「{}」，剧组 {} 人",
                me.getId(), group.getId(), title, personaIds.size());
        String opening = scenario.getOpeningDirective();
        Long gid = group.getId();
        Runnable fire = () -> {
            if (opening != null && !opening.isBlank()) {
                engine.pushDirective(gid, opening);
            } else {
                engine.ignite(gid);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fire.run();
                }
            });
        } else {
            fire.run();
        }
        return detail(group.getId(), me);
    }

    public List<TheaterVO> mine(User me) {
        List<TheaterGroup> groups = groupMapper.selectList(new QueryWrapper<TheaterGroup>()
                .eq("owner_id", me.getId()).orderByDesc("id"));
        List<TheaterVO> vos = new ArrayList<>(groups.size());
        for (TheaterGroup g : groups) {
            TheaterVO vo = TheaterVO.from(g, me.getNickname(), null);
            vo.setLiked(likedBy(g.getId(), me.getId()));
            vo.setFavorited(favoritedBy(g.getId(), me.getId()));
            vos.add(vo);
        }
        return vos;
    }

    public TheaterVO detail(Long groupId, User viewer) {
        TheaterGroup group = getOrThrow(groupId);
        visibleOrThrow(group, viewer);
        groupMapper.update(null, new UpdateWrapper<TheaterGroup>()
                .eq("id", groupId).setSql("view_count = view_count + 1"));
        String ownerName = nicknameOf(group.getOwnerId());
        TheaterVO vo = TheaterVO.from(group, ownerName, membersOf(groupId));
        vo.setAudience(sseHub.audience(groupId));
        if (viewer != null) {
            vo.setLiked(likedBy(groupId, viewer.getId()));
            vo.setFavorited(favoritedBy(groupId, viewer.getId()));
        }
        return vo;
    }

    /** 历史消息（游标分页：id < before，返回时间正序）。 */
    public List<MessageVO> messages(Long groupId, User viewer, Long before, Integer limit) {
        TheaterGroup group = getOrThrow(groupId);
        visibleOrThrow(group, viewer);
        int size = limit == null ? 50 : Math.min(Math.max(1, limit), MAX_PAGE_SIZE);
        QueryWrapper<Message> qw = new QueryWrapper<Message>().eq("group_id", groupId);
        if (before != null && before > 0) {
            qw.lt("id", before);
        }
        List<Message> list = messageMapper.selectList(qw.orderByDesc("id").last("LIMIT " + size));
        Collections.reverse(list);
        Set<Long> likedIds = viewer == null || list.isEmpty()
                ? Set.of() : likedMessageIds(viewer.getId(), list);
        return list.stream().map(m -> MessageVO.from(m, likedIds.contains(m.getId()))).toList();
    }

    // ==================== 发言与直播 ====================

    public MessageVO postMessage(User me, Long groupId, String content) {
        TheaterGroup group = getOrThrow(groupId);
        visibleOrThrow(group, me);
        if (!"LIVE".equals(group.getStatus())) {
            throw BizException.bad("本剧已杀青，回放区见");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.length() > MAX_MSG_LEN) {
            throw BizException.bad("消息不能为空，且不超过 " + MAX_MSG_LEN + " 字");
        }
        moderation.assertClean(text, "MESSAGE", me.getId());

        Message msg = new Message();
        msg.setGroupId(groupId);
        msg.setSenderType("USER");
        msg.setSenderId(me.getId());
        msg.setSenderName(me.getNickname());
        msg.setSenderAvatar(me.getAvatar());
        msg.setContent(text);
        msg.setLikeCount(0);
        msg.setIsHighlight(0);
        messageMapper.insert(msg);

        bumpCountAndHeat(groupId, 1);
        userService.addXp(me.getId(), 1);
        sseHub.message(groupId, MessageVO.from(msg, false));
        engine.ignite(groupId);
        return MessageVO.from(msg, false);
    }

    public SseEmitter stream(Long groupId, User viewer) {
        TheaterGroup group = getOrThrow(groupId);
        visibleOrThrow(group, viewer);
        return sseHub.join(groupId);
    }

    // ==================== 导演台 ====================

    public Map<String, Object> director(User me, Long groupId, DirectorReq req) {
        TheaterGroup group = getOrThrow(groupId);
        ownerOrThrow(group, me);
        if (!"LIVE".equals(group.getStatus())) {
            throw BizException.bad("本剧已杀青，无法再下指令");
        }
        String type = req.getType() == null ? "" : req.getType().toUpperCase();
        return switch (type) {
            case "DIRECTIVE" -> directDirective(group, req);
            case "EVENT_CARD" -> playEventCard(group, req);
            case "PAUSE" -> doPause(group, me);
            case "RESUME" -> doResume(group, me);
            case "MUTE", "UNMUTE" -> toggleMute(group, me, req, "MUTE".equals(type));
            case "ACTIVITY" -> adjustActivity(group, me, req);
            default -> throw BizException.bad(
                    "未知的导演操作，可用：DIRECTIVE/EVENT_CARD/PAUSE/RESUME/MUTE/UNMUTE/ACTIVITY");
        };
    }

    private Map<String, Object> directDirective(TheaterGroup group, DirectorReq req) {
        String text = req.getContent() == null ? "" : req.getContent().trim();
        if (text.isEmpty() || text.length() > MAX_DIRECTIVE_LEN) {
            throw BizException.bad("导演指令不能为空，且不超过 " + MAX_DIRECTIVE_LEN + " 字");
        }
        moderation.assertClean(text, "DIRECTIVE", group.getOwnerId());
        recordEvent(group.getId(), "DIRECTIVE", text, null, group.getOwnerId());
        Message msg = saveMessage(group.getId(), "DIRECTOR", group.getOwnerId(),
                "导演", "🎬", text);
        engine.pushDirective(group.getId(), text);
        return Map.of("message", MessageVO.from(msg, false));
    }

    private Map<String, Object> playEventCard(TheaterGroup group, DirectorReq req) {
        EventCards.Card card = EventCards.byKey(req.getEventKey());
        if (card == null) {
            throw BizException.bad("事件卡不存在，可用：" +
                    EventCards.ALL.stream().map(EventCards.Card::key).toList());
        }
        recordEvent(group.getId(), "EVENT_CARD", card.key(), null, group.getOwnerId());
        Message msg = saveSystem(group.getId(), card.announce());
        engine.pushDirective(group.getId(), card.text());
        return Map.of("message", MessageVO.from(msg, false), "card", card);
    }

    private Map<String, Object> doPause(TheaterGroup group, User me) {
        recordEvent(group.getId(), "PAUSE", null, null, me.getId());
        engine.pause(group.getId());
        saveSystem(group.getId(), "🎬 导演喊卡：演员们中场休息，观众消息照常");
        return Map.of("paused", true);
    }

    private Map<String, Object> doResume(TheaterGroup group, User me) {
        recordEvent(group.getId(), "RESUME", null, null, me.getId());
        saveSystem(group.getId(), "🎬 恢复演出，继续吵！");
        engine.resume(group.getId());
        return Map.of("paused", false);
    }

    private Map<String, Object> toggleMute(TheaterGroup group, User me, DirectorReq req, boolean mute) {
        GroupMember gm = memberOrThrow(group.getId(), req.getPersonaId());
        memberMapper.update(null, new UpdateWrapper<GroupMember>()
                .eq("id", gm.getId()).set("is_muted", mute ? 1 : 0));
        engine.syncMembers(group.getId());
        Persona p = personaMapper.selectById(gm.getPersonaId());
        String name = p == null ? "演员" + gm.getPersonaId() : p.getName();
        recordEvent(group.getId(), mute ? "MUTE" : "UNMUTE", null, gm.getPersonaId(), me.getId());
        saveSystem(group.getId(), (mute ? "🔇 " : "🔊 ") + name + (mute ? " 被导演禁言" : " 禁言解除"));
        return Map.of("personaId", gm.getPersonaId(), "muted", mute);
    }

    private Map<String, Object> adjustActivity(TheaterGroup group, User me, DirectorReq req) {
        if (req.getActivity() == null || req.getActivity() < 0 || req.getActivity() > 100) {
            throw BizException.bad("活跃度取值 0~100");
        }
        GroupMember gm = memberOrThrow(group.getId(), req.getPersonaId());
        memberMapper.update(null, new UpdateWrapper<GroupMember>()
                .eq("id", gm.getId()).set("activity", req.getActivity()));
        engine.syncMembers(group.getId());
        Persona p = personaMapper.selectById(gm.getPersonaId());
        String name = p == null ? "演员" + gm.getPersonaId() : p.getName();
        recordEvent(group.getId(), "ACTIVITY", String.valueOf(req.getActivity()),
                gm.getPersonaId(), me.getId());
        saveSystem(group.getId(), "🎚 " + name + " 的活跃度被调至 " + req.getActivity());
        return Map.of("personaId", gm.getPersonaId(), "activity", req.getActivity());
    }

    // ==================== 完结谢幕 ====================

    @Transactional
    public Map<String, Object> end(User me, Long groupId, String reason) {
        TheaterGroup group = getOrThrow(groupId);
        ownerOrThrow(group, me);
        if (!"LIVE".equals(group.getStatus())) {
            return Map.of("ended", true);
        }
        // 高光：点赞数最高的 3 条发言进入名场面
        messageMapper.update(null, new UpdateWrapper<Message>()
                .eq("group_id", groupId).gt("like_count", 0).set("is_highlight", 1)
                .orderByDesc("like_count").orderByAsc("id").last("LIMIT " + HIGHLIGHT_TOP));

        String finalReason = reason == null || reason.isBlank() ? "导演杀青" : reason.trim();
        groupMapper.update(null, new UpdateWrapper<TheaterGroup>()
                .eq("id", groupId)
                .set("status", "ENDED")
                .set("ended_at", LocalDateTime.now())
                .set("ended_reason", finalReason));
        recordEvent(groupId, "END", finalReason, null, me.getId());
        engine.end(groupId);

        TheaterGroup fresh = groupMapper.selectById(groupId);
        saveSystem(groupId, "🎬 本剧杀青！共 " + fresh.getMessageCount() + " 条台词，最终热度 " +
                fresh.getHeat() + "。感谢收看，名场面已存档");
        sseHub.ended(groupId);
        log.info("[Theater] 剧场 {} 完结，消息 {} 条", groupId, fresh.getMessageCount());
        return Map.of("ended", true, "messageCount", fresh.getMessageCount(), "heat", fresh.getHeat());
    }

    // ==================== 点赞与收藏 ====================

    /** 剧场点赞切换：true=已赞。 */
    public boolean toggleLike(User me, Long groupId) {
        TheaterGroup group = getOrThrow(groupId);
        GroupLike exists = groupLikeMapper.selectOne(new QueryWrapper<GroupLike>()
                .eq("user_id", me.getId()).eq("group_id", groupId).last("LIMIT 1"));
        if (exists != null) {
            groupLikeMapper.deleteById(exists.getId());
            groupMapper.update(null, new UpdateWrapper<TheaterGroup>()
                    .eq("id", groupId).setSql("like_count = GREATEST(0, like_count - 1)"));
            return false;
        }
        GroupLike like = new GroupLike();
        like.setUserId(me.getId());
        like.setGroupId(groupId);
        groupLikeMapper.insert(like);
        groupMapper.update(null, new UpdateWrapper<TheaterGroup>()
                .eq("id", groupId).setSql("like_count = like_count + 1"));
        userService.addXp(group.getOwnerId(), 5);
        return true;
    }

    /** 剧场收藏切换：true=已收藏。 */
    public boolean toggleFavorite(User me, Long groupId) {
        TheaterGroup group = getOrThrow(groupId);
        Favorite exists = favoriteMapper.selectOne(new QueryWrapper<Favorite>()
                .eq("user_id", me.getId()).eq("group_id", groupId).last("LIMIT 1"));
        if (exists != null) {
            favoriteMapper.deleteById(exists.getId());
            return false;
        }
        Favorite fav = new Favorite();
        fav.setUserId(me.getId());
        fav.setGroupId(groupId);
        favoriteMapper.insert(fav);
        userService.addXp(group.getOwnerId(), 3);
        return true;
    }

    /** 消息点赞切换。 */
    public Map<String, Object> toggleMessageLike(User me, Long messageId) {
        Message msg = messageMapper.selectById(messageId);
        if (msg == null) {
            throw BizException.notFound("消息不存在");
        }
        MessageLike exists = messageLikeMapper.selectOne(new QueryWrapper<MessageLike>()
                .eq("user_id", me.getId()).eq("message_id", messageId).last("LIMIT 1"));
        boolean liked;
        if (exists != null) {
            messageLikeMapper.deleteById(exists.getId());
            liked = false;
        } else {
            MessageLike like = new MessageLike();
            like.setUserId(me.getId());
            like.setMessageId(messageId);
            messageLikeMapper.insert(like);
            liked = true;
        }
        messageMapper.update(null, new UpdateWrapper<Message>()
                .eq("id", messageId)
                .setSql("like_count = like_count + " + (liked ? 1 : -1)));
        Message fresh = messageMapper.selectById(messageId);
        return Map.of("liked", liked, "likeCount", fresh.getLikeCount());
    }

    // ==================== 供其他模块复用 ====================

    public TheaterGroup getOrThrow(Long groupId) {
        TheaterGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BizException.notFound("剧场不存在");
        }
        return group;
    }

    public List<MemberVO> membersOf(Long groupId) {
        List<GroupMember> members = memberMapper.selectList(new QueryWrapper<GroupMember>()
                .eq("group_id", groupId).orderByAsc("id"));
        List<MemberVO> vos = new ArrayList<>(members.size());
        for (GroupMember gm : members) {
            Persona p = personaMapper.selectById(gm.getPersonaId());
            if (p != null) {
                vos.add(MemberVO.from(gm, p));
            }
        }
        return vos;
    }

    public String nicknameOf(Long userId) {
        User u = userMapper.selectById(userId);
        return u == null ? "神秘导演" : u.getNickname();
    }

    // ==================== 私有辅助 ====================

    /** 越权校验：PRIVATE 剧场仅 owner/ADMIN 可见（游客一律拒）。 */
    private void visibleOrThrow(TheaterGroup group, User viewer) {
        if ("PUBLIC".equals(group.getVisibility())) {
            return;
        }
        if (viewer != null && (viewer.getId().equals(group.getOwnerId()) || "ADMIN".equals(viewer.getRole()))) {
            return;
        }
        throw BizException.forbidden("这是私密剧场，只有导演自己能看");
    }

    private void ownerOrThrow(TheaterGroup group, User me) {
        if (!group.getOwnerId().equals(me.getId()) && !"ADMIN".equals(me.getRole())) {
            throw BizException.forbidden("只有本剧导演才能操作");
        }
    }

    private GroupMember memberOrThrow(Long groupId, Long personaId) {
        if (personaId == null) {
            throw BizException.bad("缺少 personaId");
        }
        GroupMember gm = memberMapper.selectOne(new QueryWrapper<GroupMember>()
                .eq("group_id", groupId).eq("persona_id", personaId).last("LIMIT 1"));
        if (gm == null) {
            throw BizException.bad("该演员不在本剧组");
        }
        return gm;
    }

    private Message saveMessage(Long groupId, String senderType, Long senderId,
                                String senderName, String avatar, String content) {
        Message msg = new Message();
        msg.setGroupId(groupId);
        msg.setSenderType(senderType);
        msg.setSenderId(senderId);
        msg.setSenderName(senderName);
        msg.setSenderAvatar(avatar);
        msg.setContent(content);
        msg.setLikeCount(0);
        msg.setIsHighlight(0);
        messageMapper.insert(msg);
        bumpCountAndHeat(groupId, 1);
        sseHub.message(groupId, MessageVO.from(msg, false));
        return msg;
    }

    private Message saveSystem(Long groupId, String text) {
        return saveMessage(groupId, "SYSTEM", null, "系统", "📢", text);
    }

    /** 消息数 +1 且热度随消息节奏 +1（封顶 100）。 */
    private void bumpCountAndHeat(Long groupId, int heatBonus) {
        groupMapper.update(null, new UpdateWrapper<TheaterGroup>()
                .eq("id", groupId)
                .setSql("message_count = message_count + 1")
                .setSql("heat = LEAST(100, heat + " + heatBonus + ")"));
    }

    private void recordEvent(Long groupId, String type, String content, Long personaId, Long operatorId) {
        DirectorEvent event = new DirectorEvent();
        event.setGroupId(groupId);
        event.setType(type);
        event.setContent(content);
        event.setPersonaId(personaId);
        event.setOperatorId(operatorId);
        eventMapper.insert(event);
    }

    private boolean likedBy(Long groupId, Long userId) {
        return groupLikeMapper.selectCount(new QueryWrapper<GroupLike>()
                .eq("user_id", userId).eq("group_id", groupId)) > 0;
    }

    private boolean favoritedBy(Long groupId, Long userId) {
        return favoriteMapper.selectCount(new QueryWrapper<Favorite>()
                .eq("user_id", userId).eq("group_id", groupId)) > 0;
    }

    private Set<Long> likedMessageIds(Long userId, List<Message> messages) {
        List<Long> ids = messages.stream().map(Message::getId).toList();
        return messageLikeMapper.selectList(new QueryWrapper<MessageLike>()
                        .eq("user_id", userId).in("message_id", ids))
                .stream().map(MessageLike::getMessageId).collect(java.util.stream.Collectors.toSet());
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "……";
    }
}
