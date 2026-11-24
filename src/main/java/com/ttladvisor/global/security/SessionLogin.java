package com.ttladvisor.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * 회원가입·로그인 성공 후 서버 세션을 만든다.
 * JSON 로그인 API를 직접 만들었으므로 Spring Security 폼 로그인이 해 주던 일을 여기서 한다.
 */
@Component
public class SessionLogin {

    private static final List<SimpleGrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();
    private final SessionAuthenticationStrategy sessionFixation = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfTokenRepository;

    public SessionLogin(SecurityContextRepository contextRepository, CsrfTokenRepository csrfTokenRepository) {
        this.contextRepository = contextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    public void login(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, AUTHORITIES);

        // 1. 로그인 전에 쓰던 세션이 있으면 ID를 바꾼다 (세션 고정 공격 방지)
        sessionFixation.onAuthentication(authentication, request, response);

        // 2. 인증 정보를 세션에 저장한다. Spring Security 6부터는 직접 저장해야 다음 요청에 이어진다
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        contextRepository.saveContext(context, request, response);

        // 3. CSRF 토큰도 새로 발급한다 (로그인 전 토큰을 계속 쓰지 않게)
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
    }
}
