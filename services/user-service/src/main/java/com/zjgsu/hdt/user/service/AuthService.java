package com.zjgsu.hdt.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zjgsu.hdt.user.dto.LoginRequest;
import com.zjgsu.hdt.user.dto.LoginResponse;
import com.zjgsu.hdt.user.exception.InvalidCredentialsException;
import com.zjgsu.hdt.user.model.AppUser;
import com.zjgsu.hdt.user.repository.UserRepository;

/**
 * 登录业务：校验口令 + 签发 JWT。
 *
 * <p>口令校验只走 {@link PasswordEncoder#matches(CharSequence, String)}，
 * 不存在"把明文和库里的值比较"这条路径 —— 库里根本没有明文。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * 校验登录并签发 Token。
     *
     * @param request 登录名与口令
     * @return Token 与基本身份信息
     * @throws InvalidCredentialsException 用户名不存在或口令不匹配（对外统一 401）
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return new LoginResponse(
                jwtTokenService.issue(user),
                "Bearer",
                jwtTokenService.ttlSeconds(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name()
        );
    }
}
