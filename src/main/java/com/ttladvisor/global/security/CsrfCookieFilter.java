package com.ttladvisor.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 매 요청마다 CSRF 토큰을 읽어, 쿠키가 없으면 응답에 {@code XSRF-TOKEN} 쿠키가 실리게 한다.
 *
 * <p>Spring Security는 토큰을 누군가 읽을 때까지 만들지 않는다(지연 로딩). 서버가 HTML을 그리지 않는 SPA는
 * 토큰을 읽을 일이 없어서, 이 필터가 없으면 GET 요청만으로는 쿠키를 받지 못하고 첫 POST가 403이 된다.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
