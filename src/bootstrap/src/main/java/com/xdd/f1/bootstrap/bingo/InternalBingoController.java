package com.xdd.f1.bootstrap.bingo;

import com.xdd.f1.application.bingo.BingoService;
import com.xdd.f1.bootstrap.auth.InternalCurrentParticipant;
import com.xdd.f1.contract.bingo.BingoDetailsDto;
import com.xdd.f1.domain.league.LeagueParticipant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/internal/v1/leagues/{leagueId}/bingo/boards/me")
public class InternalBingoController {
    private final BingoService service;

    public InternalBingoController(BingoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<BingoDetailsDto> getOwn(
            @InternalCurrentParticipant LeagueParticipant participant,
            @RequestParam Optional<Integer> season, @RequestParam(defaultValue = "en") String lang
    ) {
        var language = BingoDtoMapper.normalizeLanguage(lang);
        var details = service.getOwn(participant.userId(), participant.leagueId(), season);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(BingoDtoMapper.toDto(details, language));
    }

    @PutMapping
    public ResponseEntity<BingoDetailsDto> createOwn(
            @InternalCurrentParticipant LeagueParticipant participant,
            @RequestParam Optional<Integer> season, @RequestParam(defaultValue = "en") String lang
    ) {
        var language = BingoDtoMapper.normalizeLanguage(lang);
        var details = service.createOwn(participant.userId(), participant.leagueId(), season);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(BingoDtoMapper.toDto(details, language));
    }
}
