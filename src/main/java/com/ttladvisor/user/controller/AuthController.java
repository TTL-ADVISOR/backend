package com.ttladvisor.user.controller;

import com.ttladvisor.global.error.ApiException;
import com.ttladvisor.global.error.ErrorCode;
import com.ttladvisor.global.security.AuthUser;
import com.ttladvisor.global.security.SessionLogin;
import com.ttladvisor.user.domain.entity.User;
import com.ttladvisor.user.dto.request.LoginRequest;
import com.ttladvisor.user.dto.request.SignupRequest;
import com.ttladvisor.user.dto.response.UserResponse;
import com.ttladvisor.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입·로그인·내 정보 (API 0-1, 0-2, 0-4). 로그아웃(0-3)은 SecurityConfig의 logout 필터가 처리한다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final SessionLogin sessionLogin;

    public AuthController(UserService userService, SessionLogin sessionLogin) {
        this.userService = userService;
        this.sessionLogin = sessionLogin;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest body,
                                               HttpServletRequest request, HttpServletResponse response) {
        User user = userService.signup(body.name(), body.email(), body.password(), body.company());
        sessionLogin.login(AuthUser.of(user), request, response);
        return ResponseEntity.created(URI.create("/api/auth/me")).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest body,
                              HttpServletRequest request, HttpServletResponse response) {
        User user = userService.authenticate(body.email(), body.password());
        sessionLogin.login(AuthUser.of(user), request, response);
        return UserResponse.from(user);
    }

    /** 로그인 여부 확인. 로그인하지 않았어도 응답에 XSRF-TOKEN 쿠키가 담긴다. */
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return AuthUser.from(authentication)
                .flatMap(user -> userService.find(user.id()))
                .map(UserResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }
}
