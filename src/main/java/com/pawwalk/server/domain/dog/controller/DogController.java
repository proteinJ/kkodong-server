package com.pawwalk.server.domain.dog.controller;

import com.pawwalk.server.domain.dog.dto.DogRequest;
import com.pawwalk.server.domain.dog.dto.DogResponse;
import com.pawwalk.server.domain.dog.service.DogService;
import com.pawwalk.server.global.common.ApiResponse;
import com.pawwalk.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Dog", description = "반려견 등록/삭제 API")
@RestController
@RequestMapping("/api/v1/dogs")
@RequiredArgsConstructor
public class DogController {

    private final DogService dogService;

    @Operation(summary = "반려견 등록", description = "로그인한 회원의 반려견 정보를 등록합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<DogResponse.registration>> dogRegistration(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Valid @RequestBody DogRequest.registration request) {
        DogResponse.registration response = dogService.animalRegistration(principal.getMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("반려견 등록 완료", response));
    }

    @Operation(summary = "반려견 삭제", description = "로그인한 회원 소유의 반려견을 삭제합니다.")
    @DeleteMapping("/{dogId}")
    public ResponseEntity<ApiResponse<String>> dogDelete(
            @AuthenticationPrincipal PrincipalDetails principal,
            @PathVariable UUID dogId) {
        dogService.animalDelete(principal.getMemberId(), dogId);
        return ResponseEntity.ok(ApiResponse.success("반려견 삭제 완료"));
    }

    @Operation(summary = "반려견 정보 수정", description = "로그인한 회원 소유의 반려견 정보를 수정합니다.")
    @PatchMapping("/{dogId}")
    public ResponseEntity<ApiResponse<DogResponse.patch>> dogPatch(
            @AuthenticationPrincipal PrincipalDetails principal,
            @PathVariable UUID dogId,
            @Valid @RequestBody DogRequest.patch request) {
        DogResponse.patch response = dogService.animalPatch(principal.getMemberId(), dogId, request);
        return ResponseEntity.ok(ApiResponse.success("반려견 수정 완료", response));
    }
}
