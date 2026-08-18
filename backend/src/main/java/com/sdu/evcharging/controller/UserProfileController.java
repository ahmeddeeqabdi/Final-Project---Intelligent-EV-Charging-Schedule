package com.sdu.evcharging.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sdu.evcharging.dto.auth.UpdateUserConstraintsRequest;
import com.sdu.evcharging.dto.auth.UserConstraintsResponse;
import com.sdu.evcharging.security.AuthUserPrincipal;
import com.sdu.evcharging.service.auth.UserConstraintsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/user/me/constraints")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserConstraintsService userConstraintsService;

    @GetMapping
    public ResponseEntity<UserConstraintsResponse> getConstraints(
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ResponseEntity.ok(userConstraintsService.getByUserId(principal.userId()));
    }

    @PutMapping
    public ResponseEntity<UserConstraintsResponse> updateConstraints(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody UpdateUserConstraintsRequest request
    ) {
        return ResponseEntity.ok(userConstraintsService.updateByUserId(principal.userId(), request));
    }
}
