package com.ttladvisor.user.service;

import com.ttladvisor.global.error.ApiException;
import com.ttladvisor.global.error.ErrorCode;
import com.ttladvisor.user.domain.entity.User;
import com.ttladvisor.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    /** BCrypt는 입력의 앞 72바이트만 쓰고, 넘으면 Spring Security가 예외를 던진다. 한글은 한 글자가 3바이트다. */
    static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * 없는 이메일로 로그인할 때도 해시 비교를 한 번 해서, 응답 시간으로 가입 여부를 알 수 없게 한다.
     */
    private final String dummyHash;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public User signup(String name, String email, String password, String company) {
        if (exceedsBcryptLimit(password)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    Map.of("password", "비밀번호가 너무 깁니다. 한글은 24자, 영문·숫자는 64자까지 쓸 수 있습니다."));
        }
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = new User(name.strip(), normalizedEmail, passwordEncoder.encode(password),
                blankToNull(company), clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 존재 확인과 저장 사이에 같은 이메일이 먼저 가입된 경우 (UNIQUE 제약이 최종 방어선)
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    /**
     * 이메일·비밀번호가 맞으면 회원을 돌려준다. 이메일이 없을 때, 비밀번호가 틀릴 때,
     * 소셜 전용 회원일 때 모두 같은 예외를 던진다 (가입 여부 노출 방지).
     */
    @Transactional(readOnly = true)
    public User authenticate(String email, String password) {
        Optional<User> found = users.findByEmail(normalizeEmail(email));
        String hash = found.map(User::getPasswordHash).orElse(null);
        boolean matches = !exceedsBcryptLimit(password)
                && passwordEncoder.matches(password, hash != null ? hash : dummyHash)
                && hash != null;
        if (!matches) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return found.get();
    }

    @Transactional(readOnly = true)
    public Optional<User> find(long userId) {
        return users.findById(userId);
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
