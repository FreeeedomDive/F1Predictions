package com.xdd.f1.bootstrap.auth;

import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantRepository;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueParticipantId;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

public class InternalCurrentParticipantArgumentResolver implements HandlerMethodArgumentResolver {
    private final LeagueParticipantRepository participants;

    public InternalCurrentParticipantArgumentResolver(LeagueParticipantRepository participants) {
        this.participants = participants;
    }

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return parameter.hasParameterAnnotation(InternalCurrentParticipant.class)
                && parameter.getParameterType() == LeagueParticipant.class;
    }

    @Override
    public @NonNull Object resolveArgument(
            @NonNull MethodParameter parameter,
            @Nullable ModelAndViewContainer container,
            @NonNull NativeWebRequest request,
            @Nullable WebDataBinderFactory binderFactory
    ) {
        var participantId = new LeagueParticipantId(parseId(request.getHeader("F1P-ParticipantId"), "F1P-ParticipantId"));
        var variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        var leagueId = variables instanceof Map<?, ?> values ? (String) values.get("leagueId") : null;
        return participants.findByLeagueAndId(new LeagueId(parseId(leagueId, "leagueId")), participantId)
                .orElseThrow(() -> new LeagueParticipantNotFoundException(participantId));
    }

    private UUID parseId(@Nullable String value, String name) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must contain a valid UUID");
        }
    }
}
