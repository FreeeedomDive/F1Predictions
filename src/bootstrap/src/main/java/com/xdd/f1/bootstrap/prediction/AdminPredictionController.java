package com.xdd.f1.bootstrap.prediction;

import com.xdd.f1.application.prediction.PredictionService;
import com.xdd.f1.contract.common.ListResponseDto;
import com.xdd.f1.contract.prediction.AdminPredictionDto;
import com.xdd.f1.contract.prediction.PredictionDto;
import com.xdd.f1.contract.prediction.SavePredictionRequestDto;
import com.xdd.f1.domain.prediction.PredictionId;
import com.xdd.f1.domain.race.RaceId;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/adminApi/v1/races/{raceId}/predictions")
public class AdminPredictionController {
    private final PredictionService service;

    public AdminPredictionController(PredictionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ListResponseDto<AdminPredictionDto>> getAll(@PathVariable UUID raceId) {
        var predictions = service.findAllForAdmin(new RaceId(raceId)).stream()
                .map(details -> new AdminPredictionDto(PredictionDtoMapper.toDto(details.prediction()),
                        details.participantName(), details.leagueName()))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(new ListResponseDto<>(predictions));
    }

    @PutMapping("/{predictionId}")
    public ResponseEntity<PredictionDto> save(
            @PathVariable UUID raceId,
            @PathVariable UUID predictionId,
            @RequestBody SavePredictionRequestDto request
    ) {
        var prediction = service.saveForAdmin(new RaceId(raceId), new PredictionId(predictionId),
                PredictionDtoMapper.toDomain(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(PredictionDtoMapper.toDto(prediction));
    }
}
