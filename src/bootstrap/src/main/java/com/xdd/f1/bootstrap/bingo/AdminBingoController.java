package com.xdd.f1.bootstrap.bingo;

import com.xdd.f1.application.bingo.BingoAdminService;
import com.xdd.f1.application.bingo.BingoService;
import com.xdd.f1.bootstrap.auth.RequireRights;
import com.xdd.f1.contract.bingo.BingoAdminCardDto;
import com.xdd.f1.contract.bingo.BingoAdminDetailsDto;
import com.xdd.f1.contract.bingo.SaveBingoCardRequestDto;
import com.xdd.f1.contract.bingo.UpdateBingoProgressRequestDto;
import com.xdd.f1.domain.bingo.BingoCardId;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.rights.Right;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequireRights(Right.EDIT_BINGO)
@RequestMapping("/adminApi/v1/bingo")
public class AdminBingoController {
    private final BingoAdminService admin;
    private final BingoService bingo;

    public AdminBingoController(BingoAdminService admin, BingoService bingo) {
        this.admin = admin;
        this.bingo = bingo;
    }

    @GetMapping
    public ResponseEntity<BingoAdminDetailsDto> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(BingoDtoMapper.toDto(admin.getDetails()));
    }

    @PostMapping("/cards")
    public ResponseEntity<BingoAdminCardDto> create(@RequestBody SaveBingoCardRequestDto request) {
        BingoDtoMapper.validate(request);
        var card = admin.createCard(BingoDtoMapper.toTranslations(request.translations()),
                BingoDtoMapper.toDomain(request.probability()), request.requiredRepeats());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(BingoDtoMapper.toAdminDto(card));
    }

    @PutMapping("/cards/{cardId}")
    public ResponseEntity<BingoAdminCardDto> update(@PathVariable UUID cardId, @RequestBody SaveBingoCardRequestDto request) {
        BingoDtoMapper.validate(request);
        var card = admin.updateCard(new BingoCardId(cardId), BingoDtoMapper.toTranslations(request.translations()),
                BingoDtoMapper.toDomain(request.probability()), request.requiredRepeats());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(BingoDtoMapper.toAdminDto(card));
    }

    @DeleteMapping("/cards/{cardId}")
    public ResponseEntity<Void> delete(@PathVariable UUID cardId) {
        admin.deleteCard(new BingoCardId(cardId));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/cards/copy-previous")
    public ResponseEntity<BingoAdminDetailsDto> copyPrevious() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(BingoDtoMapper.toDto(admin.copyPrevious()));
    }

    @PutMapping("/cards/{cardId}/progress")
    public ResponseEntity<BingoAdminCardDto> updateProgress(@PathVariable UUID cardId,
                                                            @RequestBody UpdateBingoProgressRequestDto request) {
        if (request.completedRepeats() == null) {
            throw new DomainValidationException("Completed repeats are required");
        }
        var card = bingo.updateProgress(new BingoCardId(cardId), request.completedRepeats());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(BingoDtoMapper.toAdminDto(card));
    }
}
