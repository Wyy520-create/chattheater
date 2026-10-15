package com.chattheater.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chattheater.common.BizException;
import com.chattheater.security.JwtUtil;
import com.chattheater.user.dto.AuthDtos.LoginReq;
import com.chattheater.user.dto.AuthDtos.LoginResp;
import com.chattheater.user.dto.AuthDtos.RegisterReq;
import com.chattheater.user.dto.UserVO;
import com.chattheater.user.entity.User;
import com.chattheater.user.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    public LoginResp register(RegisterReq req) {
        Long exists = userMapper.selectCount(new QueryWrapper<User>().eq("username", req.getUsername()));
        if (exists > 0) {
            throw BizException.bad("用户名已被占用");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(encoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setAvatar("🎬");
        user.setRole("USER");
        user.setXp(0);
        user.setStatus(1);
        userMapper.insert(user);
        return new LoginResp(jwtUtil.issue(user.getId()), UserVO.from(user));
    }

    public LoginResp login(LoginReq req) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", req.getUsername()));
        if (user == null || !encoder.matches(req.getPassword(), user.getPasswordHash())) {
            // 统一"用户名或密码错误"，避免账号枚举
            throw BizException.bad("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw BizException.forbidden("账号已被封禁");
        }
        return new LoginResp(jwtUtil.issue(user.getId()), UserVO.from(user));
    }

    public UserVO me(User user) {
        return UserVO.from(user);
    }

    public UserVO updateProfile(User user, String nickname, String avatar) {
        User update = new User();
        update.setId(user.getId());
        update.setNickname(nickname);
        if (avatar != null && !avatar.isBlank()) {
            update.setAvatar(avatar);
        }
        userMapper.updateById(update);
        User fresh = userMapper.selectById(user.getId());
        return UserVO.from(fresh);
    }

    /** 经验值原子自增（内部调用，delta 可信）。 */
    public void addXp(Long userId, int delta) {
        if (userId == null || delta == 0) {
            return;
        }
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", userId).setSql("xp = xp + " + delta));
    }
}
