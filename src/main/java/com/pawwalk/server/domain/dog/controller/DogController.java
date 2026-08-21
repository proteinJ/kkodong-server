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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Dog", description = "반려견 등록/삭제 API")
@RestController
@RequestMapping("/api/v1/dogs")
@RequiredArgsConstructor
public class DogController {

    private final DogService dogService;

    @Operation(summary = "반려견 등록", description = "로그인한 회원의 반려견 정보를 등록합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<DogResponse.detailInfo>> dogRegistration(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Valid @RequestBody DogRequest.registration request) {
        DogResponse.detailInfo response = dogService.animalRegistration(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("반려견 등록 완료", response));
    }

    @Operation(summary = "반려견 삭제", description = "로그인한 회원 소유의 반려견을 삭제합니다.")
    @DeleteMapping("/{dogId}")
    public ResponseEntity<ApiResponse<String>> dogDelete(
            @AuthenticationPrincipal PrincipalDetails principal,
            @PathVariable UUID dogId) {
        dogService.animalDelete(principal.getUserId(), dogId);
        return ResponseEntity.ok(ApiResponse.success("반려견 삭제 완료"));
    }

    @Operation(summary = "반려견 정보 수정", description = "로그인한 회원 소유의 반려견 정보를 수정합니다.")
    @PatchMapping("/{dogId}")
    public ResponseEntity<ApiResponse<DogResponse.patch>> dogPatch(
            @AuthenticationPrincipal PrincipalDetails principal,
            @PathVariable UUID dogId,
            @Valid @RequestBody DogRequest.patch request) {
        DogResponse.patch response = dogService.animalPatch(principal.getUserId(), dogId, request);
        return ResponseEntity.ok(ApiResponse.success("반려견 수정 완료", response));
    }

    @Operation(summary = "내 반려견 상세 조회", description = "(단일) 로그인한 회원 소유의 반려견 정보를 상세 조회 합니다.")
    @GetMapping("/{dogId}")
    public ResponseEntity<ApiResponse<DogResponse.detailInfo>> dogDetailGet(
            @AuthenticationPrincipal PrincipalDetails principal,
            @PathVariable UUID dogId) {
        DogResponse.detailInfo response = dogService.animalGet(principal.getUserId(), dogId);
        return ResponseEntity.ok(ApiResponse.success("반려견 상세 조회 완료", response));
    }

    @Operation(summary = "내 반려견 목록 조회", description = "(다중) 로그인한 회원 소유의 반려견 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DogResponse.detailInfo>>> dogListGet(
            @AuthenticationPrincipal PrincipalDetails principal) {
        List<DogResponse.detailInfo> response = dogService.animalListGet(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("반려견 목록 조회 완료", response));
    }

    @Operation(summary = "내 반려견 이미지 변경", description = "내 반려견 이미지를 추가/수정 합니다.")
    @PostMapping(value = "/{dogId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DogResponse.detailInfo>> dogPhotoUpdate(
            @AuthenticationPrincipal PrincipalDetails principal,
            @PathVariable UUID dogId,
            @RequestParam("file") MultipartFile file) {
        DogResponse.detailInfo response = dogService.animalPhoto(principal.getUserId(), dogId, file);
        return ResponseEntity.ok(ApiResponse.success("반려견 이미지 변경 완료", response));
    }
}
