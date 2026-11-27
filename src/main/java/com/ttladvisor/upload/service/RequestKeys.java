package com.ttladvisor.upload.service;

import java.util.Arrays;

/**
 * 요청 로그의 캐시 키와 리소스 키 (docs/DESIGN.md 3장).
 *
 * <ul>
 *   <li>리소스 키: 쿼리를 뺀 경로. 쓰기 요청이 같은 리소스의 캐시를 무효화하는 단위 ({@code /users/123})</li>
 *   <li>캐시 키: 메서드 + 경로 + 정렬한 쿼리. 실제 캐시 항목 하나 ({@code GET /users/123?a=1&z=2})</li>
 * </ul>
 * 경로를 패턴({@code /users/:id})으로 묶는 정규화는 분석 시점의 일이라 여기서 하지 않는다.
 */
public final class RequestKeys {

    private RequestKeys() {
    }

    public static String resourceKey(String path) {
        int query = path.indexOf('?');
        return query < 0 ? path : path.substring(0, query);
    }

    /**
     * 쿼리 파라미터를 문자열 순서로 정렬해 {@code ?a=1&b=2}와 {@code ?b=2&a=1}을 같은 키로 만든다.
     * 빈 파라미터({@code a=1&&b=2}, 끝의 {@code ?})는 버린다. 값의 인코딩은 원문 그대로 둔다.
     */
    public static String cacheKey(String method, String path) {
        String resource = resourceKey(path);
        int query = path.indexOf('?');
        if (query < 0) {
            return method + " " + resource;
        }
        String[] params = Arrays.stream(path.substring(query + 1).split("&"))
                .filter(param -> !param.isEmpty())
                .sorted()
                .toArray(String[]::new);
        return params.length == 0
                ? method + " " + resource
                : method + " " + resource + "?" + String.join("&", params);
    }
}
