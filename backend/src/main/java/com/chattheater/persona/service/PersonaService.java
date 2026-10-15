package com.chattheater.persona.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chattheater.ai.LlmGateway;
import com.chattheater.common.BizException;
import com.chattheater.moderation.ModerationService;
import com.chattheater.persona.dto.PersonaDtos.SaveReq;
import com.chattheater.persona.dto.PersonaDtos.TryChatResp;
import com.chattheater.persona.dto.PersonaVO;
import com.chattheater.persona.entity.Persona;
import com.chattheater.persona.entity.PersonaLike;
import com.chattheater.persona.mapper.PersonaLikeMapper;
import com.chattheater.persona.mapper.PersonaMapper;
import com.chattheater.security.UserContext;
import com.chattheater.user.entity.User;
import com.chattheater.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PersonaService {

    public static final List<String> ARCHETYPES = List.of(
            "毒舌", "杠精", "慈祥", "卷王", "社恐", "吃瓜", "显眼包", "暴躁", "阴阳", "财迷");

    private final PersonaMapper personaMapper;
    private final PersonaLikeMapper likeMapper;
    private final LlmGateway llmGateway;
    private final ModerationService moderation;
    private final UserService userService;

    public PersonaService(PersonaMapper personaMapper, PersonaLikeMapper likeMapper,
                          LlmGateway llmGateway, ModerationService moderation, UserService userService) {
        this.personaMapper = personaMapper;
        this.likeMapper = likeMapper;
        this.llmGateway = llmGateway;
        this.moderation = moderation;
        this.userService = userService;
    }

    /** 人设市场（游客可访问，liked 恒为 false）。 */
    public Page<PersonaVO> market(int page, int size, String archetype, String keyword, String sort) {
        QueryWrapper<Persona> qw = new QueryWrapper<Persona>().eq("status", 1);
        if (archetype != null && !archetype.isBlank()) {
            qw.eq("archetype", archetype);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like("name", keyword).or().like("tagline", keyword));
        }
        if ("new".equals(sort)) {
            qw.orderByDesc("id");
        } else {
            qw.orderByDesc("like_count").orderByDesc("id");
        }
        Page<Persona> result = personaMapper.selectPage(new Page<>(page, Math.min(size, 50)), qw);
        Set<Long> likedIds = likedIds(result.getRecords());
        Page<PersonaVO> vo = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        vo.setRecords(result.getRecords().stream()
                .map(p -> PersonaVO.from(p, likedIds.contains(p.getId()))).toList());
        return vo;
    }

    public PersonaVO detail(Long id) {
        Persona persona = getOrThrow(id);
        User me = UserContext.get();
        boolean liked = me != null && likeMapper.selectCount(new QueryWrapper<PersonaLike>()
                .eq("user_id", me.getId()).eq("persona_id", id)) > 0;
        return PersonaVO.from(persona, liked);
    }

    public PersonaVO create(User me, SaveReq req) {
        if (!ARCHETYPES.contains(req.getArchetype())) {
            throw BizException.bad("原型不合法，可选：" + String.join("/", ARCHETYPES));
        }
        String all = joinAll(req);
        moderation.assertClean(all, "PERSONA", me.getId());

        Persona persona = new Persona();
        persona.setOwnerId(me.getId());
        persona.setName(req.getName());
        persona.setAvatar(blankToDefault(req.getAvatar(), "🎭"));
        persona.setArchetype(req.getArchetype());
        persona.setTagline(req.getTagline());
        persona.setTraits(req.getTraits());
        persona.setSpeakingStyle(req.getSpeakingStyle());
        persona.setBackground(req.getBackground());
        persona.setTaboo(req.getTaboo());
        persona.setIsOfficial(0);
        persona.setUseCount(0);
        persona.setLikeCount(0);
        persona.setStatus(1);
        personaMapper.insert(persona);
        userService.addXp(me.getId(), 5);
        return PersonaVO.from(persona, false);
    }

    public PersonaVO update(User me, Long id, SaveReq req) {
        Persona persona = ownedOrThrow(id, me);
        if (!ARCHETYPES.contains(req.getArchetype())) {
            throw BizException.bad("原型不合法，可选：" + String.join("/", ARCHETYPES));
        }
        moderation.assertClean(joinAll(req), "PERSONA", id);

        persona.setName(req.getName());
        if (req.getAvatar() != null && !req.getAvatar().isBlank()) {
            persona.setAvatar(req.getAvatar());
        }
        persona.setArchetype(req.getArchetype());
        persona.setTagline(req.getTagline());
        persona.setTraits(req.getTraits());
        persona.setSpeakingStyle(req.getSpeakingStyle());
        persona.setBackground(req.getBackground());
        persona.setTaboo(req.getTaboo());
        personaMapper.updateById(persona);
        return PersonaVO.from(persona, false);
    }

    public void delete(User me, Long id) {
        Persona persona = ownedOrThrow(id, me);
        personaMapper.deleteById(persona.getId());
        likeMapper.delete(new QueryWrapper<PersonaLike>().eq("persona_id", id));
    }

    /** 点赞/取消点赞（幂等切换，唯一约束兜底）。 */
    public boolean toggleLike(User me, Long id) {
        getOrThrow(id);
        QueryWrapper<PersonaLike> qw = new QueryWrapper<PersonaLike>()
                .eq("user_id", me.getId()).eq("persona_id", id);
        PersonaLike existing = likeMapper.selectOne(qw);
        if (existing != null) {
            likeMapper.deleteById(existing.getId());
            personaMapper.update(null, new UpdateWrapper<Persona>()
                    .eq("id", id).setSql("like_count = like_count - 1"));
            return false;
        }
        PersonaLike like = new PersonaLike();
        like.setUserId(me.getId());
        like.setPersonaId(id);
        likeMapper.insert(like);
        personaMapper.update(null, new UpdateWrapper<Persona>()
                .eq("id", id).setSql("like_count = like_count + 1"));
        return true;
    }

    public List<PersonaVO> mine(User me) {
        if (me == null) {
            throw BizException.unauthorized("请先登录");
        }
        List<Persona> list = personaMapper.selectList(new QueryWrapper<Persona>()
                .eq("owner_id", me.getId()).orderByDesc("id"));
        return list.stream().map(p -> PersonaVO.from(p, false)).collect(Collectors.toList());
    }

    /** 人设工坊试聊：不落库、不建组，直接单轮对话。 */
    public TryChatResp tryChat(User me, Long id, String message) {
        Persona persona = getOrThrow(id);
        moderation.assertClean(message, "TRYCHAT", id);
        LlmGateway.Reply reply = llmGateway.tryTalk(persona, message);
        return new TryChatResp(reply.text(), reply.usedMock(), reply.upstreamError());
    }

    /** 剧场创建时调用：人设被采用计数。 */
    public void markAdopted(List<Long> personaIds) {
        if (personaIds == null || personaIds.isEmpty()) {
            return;
        }
        personaMapper.update(null, new UpdateWrapper<Persona>()
                .in("id", personaIds).setSql("use_count = use_count + 1"));
    }

    public Persona getOrThrow(Long id) {
        Persona persona = personaMapper.selectById(id);
        if (persona == null || persona.getStatus() == null || persona.getStatus() == 0) {
            throw BizException.notFound("人设不存在或已下架");
        }
        return persona;
    }

    private Persona ownedOrThrow(Long id, User me) {
        Persona persona = personaMapper.selectById(id);
        if (persona == null) {
            throw BizException.notFound("人设不存在");
        }
        if (persona.getIsOfficial() != null && persona.getIsOfficial() == 1) {
            throw BizException.forbidden("官方人设不允许修改");
        }
        if (!me.getId().equals(persona.getOwnerId())
                && !"ADMIN".equals(me.getRole())) {
            throw BizException.forbidden("只能操作自己创建的人设");
        }
        return persona;
    }

    private Set<Long> likedIds(List<Persona> personas) {
        User me = UserContext.get();
        if (me == null || personas.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = personas.stream().map(Persona::getId).toList();
        return likeMapper.selectList(new QueryWrapper<PersonaLike>()
                        .eq("user_id", me.getId()).in("persona_id", ids))
                .stream().map(PersonaLike::getPersonaId).collect(Collectors.toSet());
    }

    private String joinAll(SaveReq req) {
        return String.join(" ",
                nz(req.getName()), nz(req.getTagline()), nz(req.getTraits()),
                nz(req.getSpeakingStyle()), nz(req.getBackground()), nz(req.getTaboo()));
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private String blankToDefault(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }
}
