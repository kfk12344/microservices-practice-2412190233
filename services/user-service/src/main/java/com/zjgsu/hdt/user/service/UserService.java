package com.zjgsu.hdt.user.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zjgsu.hdt.user.dto.UserRequest;
import com.zjgsu.hdt.user.dto.UserResponse;
import com.zjgsu.hdt.user.exception.ResourceNotFoundException;
import com.zjgsu.hdt.user.model.AppUser;
import com.zjgsu.hdt.user.repository.UserRepository;

/**
 * 用户业务逻辑。
 *
 * <p>职责边界：只处理"用户档案"这一件事。
 * 本类不引用任何食物或饮食记录的类 —— 那些在别的服务里，跨过去只能走 HTTP。
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * 按 ID 查询用户。
     *
     * <p>这是 meal-service 做跨服务校验时调用的核心方法：
     * 用户不存在时抛 {@link ResourceNotFoundException}，由全局异常处理转成 404，
     * 让调用方能明确区分"用户不存在"与"用户服务挂了"。
     */
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.user(id));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse findByUsername(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在：username=" + username));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("用户名已存在：" + request.username());
        }
        AppUser user = new AppUser(
                request.username(),
                request.displayName(),
                request.dailyTargetKcal(),
                LocalDateTime.now()
        );
        return UserResponse.from(userRepository.save(user));
    }
}
