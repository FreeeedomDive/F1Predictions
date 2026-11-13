package com.xdd.f1.bootstrap.rights;

import com.xdd.f1.application.rights.RightsService;
import com.xdd.f1.bootstrap.auth.RequireRights;
import com.xdd.f1.contract.common.ListResponseDto;
import com.xdd.f1.contract.rights.RightDto;
import com.xdd.f1.contract.rights.UserRightsDto;
import com.xdd.f1.domain.rights.Right;
import com.xdd.f1.domain.user.UserId;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/adminApi/v1/rights")
@RequireRights(Right.EDIT_RIGHTS)
public class RightsController {
    private final RightsService service;

    public RightsController(RightsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ListResponseDto<UserRightsDto>> getAll() {
        var users = service.findAllUsers().stream().map(user -> new UserRightsDto(
                user.userId().value(), user.names(),
                user.rights().stream().map(right -> RightDto.valueOf(right.name())).toList()
        )).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(new ListResponseDto<>(users));
    }

    @PutMapping("/{userId}/{right}")
    public ResponseEntity<Void> grant(@PathVariable UUID userId, @PathVariable RightDto right) {
        service.grant(new UserId(userId), Right.valueOf(right.name()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/{right}")
    public ResponseEntity<Void> revoke(@PathVariable UUID userId, @PathVariable RightDto right) {
        service.revoke(new UserId(userId), Right.valueOf(right.name()));
        return ResponseEntity.noContent().build();
    }
}
