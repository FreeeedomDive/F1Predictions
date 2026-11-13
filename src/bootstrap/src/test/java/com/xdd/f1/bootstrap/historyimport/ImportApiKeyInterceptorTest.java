package com.xdd.f1.bootstrap.historyimport;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ImportApiKeyInterceptorTest {

    @Test
    void requiresAnExplicitApiKey() {
        var interceptor = new ImportApiKeyInterceptor(new ImportProperties("test-import-key"));
        var request = new MockHttpServletRequest("POST", "/importApi/v1/example");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isFalse();

        request.addHeader("X-Import-Api-Key", "wrong-key");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isFalse();

        request.removeHeader("X-Import-Api-Key");
        request.addHeader("X-Import-Api-Key", "test-import-key");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        var withoutKey = new ImportApiKeyInterceptor(new ImportProperties(""));
        assertThat(withoutKey.preHandle(request, new MockHttpServletResponse(), new Object())).isFalse();
    }
}
