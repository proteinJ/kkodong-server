package com.pawwalk.server.domain.dog.controller;

import com.pawwalk.server.domain.dog.dto.DogRequest;
import com.pawwalk.server.domain.dog.dto.DogResponse;
import com.pawwalk.server.domain.dog.service.DogService;
import com.pawwalk.server.global.common.ApiResponse;
import com.pawwalk.server.global.security.PrincipalDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dogs")
@RequiredArgsConstructor
public class DogController {

    private final DogService dogService;

    @PostMapping
    public ResponseEntity<ApiResponse<DogResponse.registration>> dogRegistration(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Valid @RequestBody DogRequest.registration request) {
        DogResponse.registration response = dogService.animalRegistration(principal.getMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("반려견 등록 완료", response));
    }

    @DeleteMapping("/{dogId}")
    public ResponseEntity<ApiResponse<String>> dogDelete(
            @AuthenticationPrincipal PrincipalDetails principal,
            @PathVariable UUID dogId) {
        dogService.animalDelete(principal.getMemberId(), dogId);
        return ResponseEntity.ok(ApiResponse.success("반려견 삭제 완료"));
    }
}
