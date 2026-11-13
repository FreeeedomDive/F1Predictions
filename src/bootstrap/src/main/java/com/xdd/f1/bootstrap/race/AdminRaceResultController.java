package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.result.RaceResultService;
import com.xdd.f1.application.race.RaceStartingGridSyncService;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.bootstrap.auth.RequireRights;
import com.xdd.f1.domain.rights.Right;
import com.xdd.f1.contract.race.result.RaceResultDto;
import com.xdd.f1.contract.race.result.RaceResultEditorDto;
import com.xdd.f1.contract.race.result.RaceResultValuesDto;
import com.xdd.f1.contract.race.result.SaveStartingGridRequestDto;
import com.xdd.f1.domain.race.RaceId;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequireRights(Right.EDIT_RACE_RESULTS)
@RequestMapping("/adminApi/v1/races/{raceId}/result")
public class AdminRaceResultController {
    private final RaceResultService service;
    private final RaceStartingGridSyncService startingGridSync;

    public AdminRaceResultController(RaceResultService service, RaceStartingGridSyncService startingGridSync) {
        this.service = service;
        this.startingGridSync = startingGridSync;
    }

    @GetMapping
    public ResponseEntity<RaceResultEditorDto> getForEditing(@CurrentUser AuthenticatedUser user, @PathVariable UUID raceId) {
        var editor = service.getForEditing(new RaceId(raceId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(RaceResultDtoMapper.toDto(editor));
    }

    @PutMapping("/draft")
    public ResponseEntity<RaceResultDto> saveDraft(@CurrentUser AuthenticatedUser user, @PathVariable UUID raceId,
                                                 @RequestBody RaceResultValuesDto request) {
        var saved = service.saveDraft(new RaceId(raceId), RaceResultDtoMapper.toDomain(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(RaceResultDtoMapper.toDto(saved));
    }

    @PostMapping("/finalize")
    public ResponseEntity<RaceResultDto> finalizeResult(@CurrentUser AuthenticatedUser user, @PathVariable UUID raceId) {
        var saved = service.finalizeResult(new RaceId(raceId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(RaceResultDtoMapper.toDto(saved));
    }

    @PostMapping("/starting-grid/sync")
    public ResponseEntity<RaceResultDto> syncStartingGrid(@CurrentUser AuthenticatedUser user, @PathVariable UUID raceId) {
        var saved = startingGridSync.syncStartingGrid(new RaceId(raceId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(RaceResultDtoMapper.toDto(saved));
    }

    @PutMapping("/starting-grid")
    public ResponseEntity<RaceResultDto> saveStartingGrid(@CurrentUser AuthenticatedUser user, @PathVariable UUID raceId,
                                                         @RequestBody SaveStartingGridRequestDto request) {
        var saved = service.saveStartingGridForAdmin(new RaceId(raceId), RaceResultDtoMapper.toDomain(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(RaceResultDtoMapper.toDto(saved));
    }
}
