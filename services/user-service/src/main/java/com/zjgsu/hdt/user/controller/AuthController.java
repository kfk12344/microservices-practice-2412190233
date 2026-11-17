package com.zjgsu.hdt.user.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zjgsu.hdt.user.dto.LoginRequest;
import com.zjgsu.hdt.user.dto.LoginResponse;
import com.zjgsu.hdt.user.service.AuthService;

/**
 * 认证接口（第 09 周新增）。
 *
 * <p>{@code POST /api/auth/login} 是网关白名单里唯一的业务接口：
 * 它是拿到 Token 的入口，本身不可能要求 Token。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录并返回 JWT。
     *
     * <p>成功 200；用户名或口令错误统一 401（不区分是哪一种，避免账号枚举）。
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
