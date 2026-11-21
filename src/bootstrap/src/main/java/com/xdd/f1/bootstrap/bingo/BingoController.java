package com.xdd.f1.bootstrap.bingo;

import com.xdd.f1.application.bingo.BingoService;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.contract.bingo.BingoDetailsDto;
import com.xdd.f1.domain.league.LeagueId;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leagues/{leagueId}/bingo/boards/me")
public class BingoController {
    private final BingoService service;

    public BingoController(BingoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<BingoDetailsDto> getOwn(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID leagueId,
            @RequestParam Optional<Integer> season, @RequestParam(defaultValue = "en") String lang
    ) {
        var language = BingoDtoMapper.normalizeLanguage(lang);
        var details = service.getOwn(user.id(), new LeagueId(leagueId), season);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(BingoDtoMapper.toDto(details, language));
    }

    @PutMapping
    public ResponseEntity<BingoDetailsDto> createOwn(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID leagueId,
            @RequestParam Optional<Integer> season, @RequestParam(defaultValue = "en") String lang
    ) {
        var language = BingoDtoMapper.normalizeLanguage(lang);
        var details = service.createOwn(user.id(), new LeagueId(leagueId), season);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(BingoDtoMapper.toDto(details, language));
    }
}
