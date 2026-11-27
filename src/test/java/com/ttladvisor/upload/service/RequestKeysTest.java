package com.ttladvisor.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RequestKeysTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "/users/123                | /users/123",
        "/users/123?lang=ko        | /users/123",
        "/users/123?               | /users/123",
        "/                         | /"
    })
    void 리소스_키는_쿼리를_뺀_경로다(String path, String expected) {
        assertThat(RequestKeys.resourceKey(path)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "GET  | /users/123                 | GET /users/123",
        "GET  | /users/123?z=2&a=1         | GET /users/123?a=1&z=2",
        "GET  | /users/123?a=2&a=1         | GET /users/123?a=1&a=2",
        "GET  | /users/123?                | GET /users/123",
        "GET  | /users/123?a=1&&b=2&       | GET /users/123?a=1&b=2",
        "GET  | /s?q=%ED%95%9C&p=1         | GET /s?p=1&q=%ED%95%9C",
        "POST | /users/123?x=1             | POST /users/123?x=1"
    })
    void 캐시_키는_메서드와_정렬한_쿼리를_포함한다(String method, String path, String expected) {
        assertThat(RequestKeys.cacheKey(method, path)).isEqualTo(expected);
    }
}
