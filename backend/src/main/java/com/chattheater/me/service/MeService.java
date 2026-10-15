package com.chattheater.me.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chattheater.me.dto.MeDtos.OverviewVO;
import com.chattheater.persona.entity.Persona;
import com.chattheater.persona.mapper.PersonaMapper;
import com.chattheater.plaza.dto.PlazaDtos.PlazaCardVO;
import com.chattheater.plaza.service.PlazaService;
import com.chattheater.theater.entity.Favorite;
import com.chattheater.theater.entity.Message;
import com.chattheater.theater.entity.TheaterGroup;
import com.chattheater.theater.mapper.FavoriteMapper;
import com.chattheater.theater.mapper.MessageMapper;
import com.chattheater.theater.mapper.TheaterGroupMapper;
import com.chattheater.user.dto.LevelCalculator;
import com.chattheater.user.entity.User;
import com.chattheater.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 个人中心：创作总览与收藏夹。 */
@Service
public class MeService {

    private final UserMapper userMapper;
    private final TheaterGroupMapper groupMapper;
    private final PersonaMapper personaMapper;
    private final FavoriteMapper favoriteMapper;
    private final MessageMapper messageMapper;
    private final PlazaService plazaService;

    public MeService(UserMapper userMapper, TheaterGroupMapper groupMapper,
                     PersonaMapper personaMapper, FavoriteMapper favoriteMapper,
                     MessageMapper messageMapper, PlazaService plazaService) {
        this.userMapper = userMapper;
        this.groupMapper = groupMapper;
        this.personaMapper = personaMapper;
        this.favoriteMapper = favoriteMapper;
        this.messageMapper = messageMapper;
        this.plazaService = plazaService;
    }

    public OverviewVO overview(User me) {
        User fresh = userMapper.selectById(me.getId());
        int xp = fresh == null || fresh.getXp() == null ? 0 : fresh.getXp();
        OverviewVO vo = OverviewVO.from(fresh == null ? me : fresh, LevelCalculator.of(xp));
        vo.setLiveTheaters(groupMapper.selectCount(new QueryWrapper<TheaterGroup>()
                .eq("owner_id", me.getId()).eq("status", "LIVE")));
        vo.setEndedTheaters(groupMapper.selectCount(new QueryWrapper<TheaterGroup>()
                .eq("owner_id", me.getId()).eq("status", "ENDED")));
        vo.setPersonaCount(personaMapper.selectCount(new QueryWrapper<Persona>()
                .eq("owner_id", me.getId())));
        vo.setFavoriteCount(favoriteMapper.selectCount(new QueryWrapper<Favorite>()
                .eq("user_id", me.getId())));
        vo.setReceivedLikes(sumLikes("theater_group", me.getId())
                + sumLikes("persona", me.getId()));
        vo.setMyMessageCount(messageMapper.selectCount(new QueryWrapper<Message>()
                .eq("sender_type", "USER").eq("sender_id", me.getId())));
        return vo;
    }

    /** 收藏夹（按收藏时间倒序，保留顺序地转卡片）。 */
    public List<PlazaCardVO> favorites(User me) {
        List<Favorite> favorites = favoriteMapper.selectList(new QueryWrapper<Favorite>()
                .eq("user_id", me.getId()).orderByDesc("id"));
        if (favorites.isEmpty()) {
            return List.of();
        }
        List<Long> orderedIds = favorites.stream().map(Favorite::getGroupId).toList();
        Map<Long, PlazaCardVO> byId = plazaService.cardsByIds(orderedIds).stream()
                .collect(Collectors.toMap(PlazaCardVO::getId, Function.identity()));
        return orderedIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    /** 某表某用户的 like_count 合计（剧场获赞/人设获赞）。 */
    private long sumLikes(String table, Long ownerId) {
        List<Map<String, Object>> rows;
        if ("theater_group".equals(table)) {
            rows = groupMapper.selectMaps(new QueryWrapper<TheaterGroup>()
                    .select("IFNULL(SUM(like_count), 0) AS total").eq("owner_id", ownerId));
        } else {
            rows = personaMapper.selectMaps(new QueryWrapper<Persona>()
                    .select("IFNULL(SUM(like_count), 0) AS total").eq("owner_id", ownerId));
        }
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return 0;
        }
        Object total = rows.get(0).get("total");
        return total instanceof Number n ? n.longValue() : 0;
    }
}
