package com.ttladvisor.user.repository;

import com.ttladvisor.user.domain.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** @param email 소문자로 정규화한 이메일 */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
