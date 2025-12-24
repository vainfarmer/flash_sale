package org.example.flash_sale.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Result;
import org.example.flash_sale.config.JwtConfig;
import org.example.flash_sale.entity.User;
import org.example.flash_sale.mapper.UserMapper;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器（简化版，用于演示）
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final JwtConfig jwtConfig;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        if (username == null || password == null) {
            return Result.fail(400, "用户名和密码不能为空");
        }

        User user = userMapper.selectByUsername(username);
        if (user == null) {
            return Result.fail(401, "用户不存在");
        }

        // 简化密码验证（实际应使用加密比较）
        if (!password.equals(user.getPassword())) {
            return Result.fail(401, "密码错误");
        }

        if (user.getStatus() != 1) {
            return Result.fail(403, "账户已被禁用");
        }

        // 生成Token
        String token = jwtConfig.generateToken(user.getId(), user.getUsername());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());

        log.info("用户登录成功: {}", username);
        return Result.success(result);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/info")
    public Result<User> getUserInfo(@RequestHeader("Authorization") String authorization) {
        try {
            String token = authorization.replace("Bearer ", "");
            Long userId = jwtConfig.getUserIdFromToken(token);
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setPassword(null); // 不返回密码
            }
            return Result.success(user);
        } catch (Exception e) {
            return Result.fail(401, "无效的Token");
        }
    }
}

