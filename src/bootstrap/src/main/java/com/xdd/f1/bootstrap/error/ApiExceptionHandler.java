package com.xdd.f1.bootstrap.error;

import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.race.RaceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({
            LeagueNotFoundException.class,
            LeagueParticipantNotFoundException.class,
            RaceNotFoundException.class
    })
    public ResponseEntity<ProblemDetail> notFound(RuntimeException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }
}
