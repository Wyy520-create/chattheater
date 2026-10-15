package com.chattheater.plaza.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chattheater.persona.entity.Persona;
import com.chattheater.persona.mapper.PersonaMapper;
import com.chattheater.plaza.dto.PlazaDtos.PlazaCardVO;
import com.chattheater.plaza.dto.PlazaDtos.HallVO;
import com.chattheater.plaza.dto.PlazaDtos.StatsVO;
import com.chattheater.theater.dto.TheaterDtos.MessageVO;
import com.chattheater.theater.entity.GroupMember;
import com.chattheater.theater.entity.Message;
import com.chattheater.theater.entity.TheaterGroup;
import com.chattheater.theater.mapper.GroupMemberMapper;
import com.chattheater.theater.mapper.MessageMapper;
import com.chattheater.theater.mapper.TheaterGroupMapper;
import com.chattheater.theater.sse.SseHub;
import com.chattheater.user.entity.User;
import com.chattheater.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 广场：直播大厅 + 名场面馆 + 全站统计。
 * 只读、对游客开放（AuthInterceptor GET 白名单 /api/plaza/**）。
 */
@Service
public class PlazaService {

    private static final int MAX_PAGE_SIZE = 50;

    private final TheaterGroupMapper groupMapper;
    private final GroupMemberMapper memberMapper;
    private final PersonaMapper personaMapper;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final SseHub sseHub;

    public PlazaService(TheaterGroupMapper groupMapper, GroupMemberMapper memberMapper,
                        PersonaMapper personaMapper, MessageMapper messageMapper,
                        UserMapper userMapper, SseHub sseHub) {
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.personaMapper = personaMapper;
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.sseHub = sseHub;
    }

    /** 直播大厅：公开 + LIVE，按热度排序。 */
    public Page<PlazaCardVO> live(int page, int size) {
        return cards(new QueryWrapper<TheaterGroup>()
                .eq("visibility", "PUBLIC").eq("status", "LIVE")
                .orderByDesc("heat").orderByDesc("id"), page, size);
    }

    /** 名场面馆：公开 + ENDED，最近杀青在前。 */
    public Page<PlazaCardVO> ended(int page, int size) {
        return cards(new QueryWrapper<TheaterGroup>()
                .eq("visibility", "PUBLIC").eq("status", "ENDED")
                .orderByDesc("ended_at"), page, size);
    }

    /** 名场面馆详情：剧场卡片 + 高光台词（is_highlight 标记的点赞 Top3）。 */
    public HallVO hall(Long groupId) {
        TheaterGroup group = groupMapper.selectById(groupId);
        if (group == null || !"PUBLIC".equals(group.getVisibility())) {
            throw new com.chattheater.common.BizException(404, "剧目不存在或未公开");
        }
        List<Message> highlights = messageMapper.selectList(new QueryWrapper<Message>()
                .eq("group_id", groupId).eq("is_highlight", 1)
                .orderByDesc("like_count").orderByAsc("id"));
        HallVO vo = new HallVO();
        vo.setTheater(card(group));
        vo.setHighlights(highlights.stream().map(m -> MessageVO.from(m, false)).toList());
        vo.setHasHighlights(!highlights.isEmpty());
        return vo;
    }

    /** 全站统计数字带。 */
    public StatsVO stats() {
        StatsVO vo = new StatsVO();
        vo.setLiveCount(groupMapper.selectCount(new QueryWrapper<TheaterGroup>()
                .eq("visibility", "PUBLIC").eq("status", "LIVE")));
        vo.setEndedCount(groupMapper.selectCount(new QueryWrapper<TheaterGroup>()
                .eq("visibility", "PUBLIC").eq("status", "ENDED")));
        vo.setTotalMessages(messageMapper.selectCount(null));
        vo.setTotalUsers(userMapper.selectCount(null));
        vo.setTotalPersonas(personaMapper.selectCount(new QueryWrapper<Persona>().eq("status", 1)));
        vo.setOnlineAudience(sseHub.totalAudience());
        return vo;
    }

    /** 按 id 批量取卡片（个人中心收藏列表复用；顺序由调用方保持）。 */
    public List<PlazaCardVO> cardsByIds(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        return groupMapper.selectBatchIds(groupIds).stream().map(this::card).toList();
    }

    // ==================== 私有辅助 ====================

    private Page<PlazaCardVO> cards(QueryWrapper<TheaterGroup> qw, int page, int size) {
        Page<TheaterGroup> result = groupMapper.selectPage(
                new Page<>(Math.max(1, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE)), qw);
        Page<PlazaCardVO> vo = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        vo.setRecords(result.getRecords().stream().map(this::card).toList());
        return vo;
    }

    private PlazaCardVO card(TheaterGroup group) {
        List<GroupMember> members = memberMapper.selectList(new QueryWrapper<GroupMember>()
                .eq("group_id", group.getId()).orderByAsc("id"));
        List<String> names = new ArrayList<>(members.size());
        for (GroupMember gm : members) {
            Persona p = personaMapper.selectById(gm.getPersonaId());
            if (p != null) {
                names.add(p.getArchetype() + "·" + p.getName());
            }
        }
        User owner = userMapper.selectById(group.getOwnerId());
        String ownerName = owner == null ? "神秘导演" : owner.getNickname();
        return PlazaCardVO.from(group, ownerName,
                String.join(" / ", names), sseHub.audience(group.getId()));
    }
}
