package com.ttladvisor.global.security;

import com.ttladvisor.user.domain.entity.User;
import java.io.Serial;
import java.io.Serializable;
import java.util.Optional;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;

/**
 * 세션에 저장하는 로그인 사용자. 세션은 MySQL에 직렬화되어 저장되므로 꼭 필요한 값만 담는다.
 * 이름·회사처럼 바뀔 수 있는 값은 필요할 때 DB에서 다시 읽는다.
 *
 * <p>{@link #getName()}은 Spring Session의 {@code PRINCIPAL_NAME} 컬럼에 저장되어,
 * 나중에 회원별로 세션을 찾거나 모두 끊을 때 쓴다.
 */
public record AuthUser(long id) implements AuthenticatedPrincipal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static AuthUser of(User user) {
        return new AuthUser(user.getId());
    }

    /** 로그인한 요청이면 사용자를, 익명이면 빈 값을 돌려준다. */
    public static Optional<AuthUser> from(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    @Override
    public String getName() {
        return String.valueOf(id);
    }
}
