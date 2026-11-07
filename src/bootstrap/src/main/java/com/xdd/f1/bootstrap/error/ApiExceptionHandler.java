package com.xdd.f1.bootstrap.error;

import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.session.SessionNotFoundException;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.InvalidRaceStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DomainValidationException.class)
    public ProblemDetail badRequest(DomainValidationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ProblemDetail unauthorized(SessionNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler({
            LeagueNotFoundException.class,
            LeagueParticipantNotFoundException.class,
            RaceNotFoundException.class
    })
    public ProblemDetail notFound(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidRaceStatusException.class)
    public ProblemDetail invalidRaceStatus(InvalidRaceStatusException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
