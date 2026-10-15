package com.chattheater.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chattheater.user.entity.User;
import com.chattheater.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Pattern;

/**
 * 登录拦截器。支持两种取 token 方式：
 * 1. Header: Authorization: Bearer &lt;token&gt;
 * 2. 查询参数 ?token=（EventSource 无法自定义 Header，SSE 专用）
 *
 * 游客可访问的只读端点（GET 白名单）直接放行，此时 UserContext 为空，由业务代码兼容。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** GET /api/personas 与 /api/personas/{id}：人设市场对游客开放。 */
    private static final Pattern PUBLIC_PERSONA_DETAIL = Pattern.compile("/api/personas/\\d+");

    /** GET /api/theaters/{id}(/messages|/stream)：公开剧场的围观通道对游客开放。 */
    private static final Pattern PUBLIC_THEATER_DETAIL = Pattern.compile("/api/theaters/\\d+");
    private static final Pattern PUBLIC_THEATER_SUB = Pattern.compile("/api/theaters/\\d+/(messages|stream)");

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (isPublicGet(request)) {
            // 白名单只免强制登录：若请求携带有效 token 仍解析身份，
            // 保证 PRIVATE 剧场导演等“带身份的游客通道”请求能拿到 UserContext。
            fillIdentityIfPresent(request);
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isBlank()) {
            token = request.getParameter("token");
        }
        Long userId = token == null ? null : jwtUtil.parse(token);
        if (userId == null) {
            return reject(response, 401, "未登录或登录已过期");
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() == 0) {
            return reject(response, 401, "账号不存在或已被封禁");
        }
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    /** 白名单路径的尽力身份解析：token 缺失/无效/过期时静默降级为游客。 */
    private void fillIdentityIfPresent(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            if (token == null || token.isBlank()) {
                token = request.getParameter("token");
            }
            if (token == null || token.isBlank()) {
                return;
            }
            Long userId = jwtUtil.parse(token);
            if (userId == null) {
                return;
            }
            User user = userMapper.selectById(userId);
            if (user != null && user.getStatus() != null && user.getStatus() != 0) {
                UserContext.set(user);
            }
        } catch (Exception ignored) {
            // 白名单路径绝不能因 token 问题拒绝请求
        }
    }

    private boolean isPublicGet(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        // 广场全部只读开放
        if (path.startsWith("/api/plaza")) {
            return true;
        }
        // 场景剧本浏览开放
        if (path.equals("/api/scenarios") || path.matches("/api/scenarios/\\d+")) {
            return true;
        }
        // 人设市场浏览开放（列表/详情/原型枚举）
        if (path.equals("/api/personas") || path.equals("/api/personas/archetypes")
                || PUBLIC_PERSONA_DETAIL.matcher(path).matches()) {
            return true;
        }
        // 公开剧场的详情/历史消息/SSE 流/当前投票围观（PRIVATE 由业务层校验）
        if (PUBLIC_THEATER_DETAIL.matcher(path).matches()
                || PUBLIC_THEATER_SUB.matcher(path).matches()
                || path.matches("/api/theaters/\\d+/polls/active")) {
            return true;
        }
        // 擂台列表与详情对游客开放（PK 是跨剧场公共事件）
        if (path.equals("/api/pk/list") || path.matches("/api/pk/\\d+")) {
            return true;
        }
        return false;
    }

    private boolean reject(HttpServletResponse response, int status, String msg) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                java.util.Map.of("code", status, "msg", msg)));
        return false;
    }
}
