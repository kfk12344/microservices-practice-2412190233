package com.zjgsu.hdt.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zjgsu.hdt.user.model.AppUser;

/**
 * 用户 Repository。只操作本服务的 {@code app_user} 表。
 */
public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    List<AppUser> findAllByOrderByIdAsc();
}
