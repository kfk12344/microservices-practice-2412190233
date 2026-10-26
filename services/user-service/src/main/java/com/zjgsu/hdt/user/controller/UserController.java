package com.zjgsu.hdt.user.controller;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zjgsu.hdt.user.dto.UserRequest;
import com.zjgsu.hdt.user.dto.UserResponse;
import com.zjgsu.hdt.user.service.UserService;

/**
 * 用户服务 REST 接口。
 *
 * <p>除了给前端用，本接口同时是 meal-service 跨服务校验的入口，
 * 因此 {@code GET /api/users/{id}} 的行为（200 / 404）属于对外契约。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 用户列表。 */
    @GetMapping
    public List<UserResponse> list(@RequestParam(name = "username", required = false) String username) {
        return username == null ? userService.listAll() : List.of(userService.findByUsername(username));
    }

    /** 按 ID 查询单个用户；不存在返回 404（meal-service 靠这个判断用户是否存在）。 */
    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        return userService.findById(id);
    }

    /** 创建用户，成功返回 201 与 Location。 */
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }
}
