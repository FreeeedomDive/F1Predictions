package com.xdd.f1.bootstrap.auth;

import com.xdd.f1.application.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionAuthenticationInterceptorTest {

    private final SessionService sessions = mock(SessionService.class);
    private final SessionAuthenticationInterceptor interceptor = new SessionAuthenticationInterceptor(sessions);

    @Test
    void allowsAnonymousControllerWithoutCheckingSession() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/public-example");
        var response = new MockHttpServletResponse();
        var controller = new PublicController();
        var handler = new HandlerMethod(controller, PublicController.class.getDeclaredMethod("endpoint"));

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        verifyNoInteractions(sessions);
    }

    @Test
    void allowsAnonymousMethodInOtherwiseProtectedController() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/example/public");
        var response = new MockHttpServletResponse();
        var controller = new MixedController();
        var handler = new HandlerMethod(controller, MixedController.class.getDeclaredMethod("publicEndpoint"));

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        verifyNoInteractions(sessions);
    }

    @Test
    void keepsUnannotatedSiblingMethodProtectedByDefault() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/example/protected");
        var response = new MockHttpServletResponse();
        var controller = new MixedController();
        var handler = new HandlerMethod(controller, MixedController.class.getDeclaredMethod("protectedEndpoint"));
        when(sessions.find(null)).thenReturn(Optional.empty());

        assertThat(interceptor.preHandle(request, response, handler)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @AllowAnonymous
    private static class PublicController {

        public void endpoint() {
        }
    }

    private static class MixedController {

        @AllowAnonymous
        public void publicEndpoint() {
        }

        public void protectedEndpoint() {
        }
    }
}
