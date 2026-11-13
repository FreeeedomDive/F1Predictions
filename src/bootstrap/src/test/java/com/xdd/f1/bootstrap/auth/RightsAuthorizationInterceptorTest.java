package com.xdd.f1.bootstrap.auth;

import com.xdd.f1.application.rights.RightsService;
import com.xdd.f1.domain.rights.Right;
import com.xdd.f1.domain.user.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RightsAuthorizationInterceptorTest {
    private final RightsService rights = mock(RightsService.class);
    private final RightsAuthorizationInterceptor interceptor = new RightsAuthorizationInterceptor(rights);
    private final UserId userId = new UserId(UUID.randomUUID());

    @Test
    void methodOverridesClassAndAcceptsAnyListedRight() throws Exception {
        when(rights.findByUserId(userId)).thenReturn(List.of(Right.EDIT_RACE_RESULTS));
        assertThat(allowed(new Controller(), "inherited")).isTrue();
        assertThat(allowed(new Controller(), "overridden")).isFalse();
        when(rights.findByUserId(userId)).thenReturn(List.of(Right.EDIT_PREDICTIONS));
        assertThat(allowed(new Controller(), "overridden")).isTrue();
        when(rights.findByUserId(userId)).thenReturn(List.of(Right.EDIT_RACE_LINEUPS));
        assertThat(allowed(new Controller(), "overridden")).isTrue();
    }

    @Test
    void missingOrEmptyDeclarationDoesNotAllowAdministrativeHandler() throws Exception {
        when(rights.findByUserId(userId)).thenReturn(List.of(Right.values()));
        assertThat(allowed(new UndeclaredController(), "endpoint")).isFalse();
        assertThat(allowed(new Controller(), "empty")).isFalse();
    }

    private boolean allowed(Object controller, String name) throws Exception {
        var request = new MockHttpServletRequest("GET", "/adminApi/v1/test");
        request.setAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE, new AuthenticatedUser(userId));
        var response = new MockHttpServletResponse();
        var handler = new HandlerMethod(controller, controller.getClass().getDeclaredMethod(name));
        var allowed = interceptor.preHandle(request, response, handler);
        if (!allowed) assertThat(response.getStatus()).isEqualTo(403);
        return allowed;
    }

    @RequireRights(Right.EDIT_RACE_RESULTS)
    private static class Controller {
        public void inherited() {}

        @RequireRights({Right.EDIT_PREDICTIONS, Right.EDIT_RACE_LINEUPS})
        public void overridden() {}

        @RequireRights({})
        public void empty() {}
    }

    private static class UndeclaredController {
        public void endpoint() {}
    }
}
