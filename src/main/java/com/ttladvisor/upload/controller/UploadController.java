package com.ttladvisor.upload.controller;

import com.ttladvisor.global.security.AuthUser;
import com.ttladvisor.upload.domain.entity.LogUpload;
import com.ttladvisor.upload.dto.response.UploadAcceptedResponse;
import com.ttladvisor.upload.dto.response.UploadResponse;
import com.ttladvisor.upload.service.UploadService;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

/** 로그 업로드 (API 2-1~2-3). */
@RestController
public class UploadController {

    private final UploadService uploadService;
    private final ObjectMapper objectMapper;

    public UploadController(UploadService uploadService, ObjectMapper objectMapper) {
        this.uploadService = uploadService;
        this.objectMapper = objectMapper;
    }

    /** 2-1: 파일을 받아 바로 202로 응답한다. 진행 상황은 Location의 상태 API를 폴링한다. */
    @PostMapping(path = "/api/projects/{projectId}/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadAcceptedResponse> upload(@AuthenticationPrincipal AuthUser user,
                                                         @PathVariable long projectId,
                                                         @RequestPart("file") MultipartFile file) {
        LogUpload upload = uploadService.upload(user.id(), projectId, file);
        return ResponseEntity.accepted()
                .location(URI.create("/api/uploads/" + upload.getId()))
                .body(UploadAcceptedResponse.from(upload));
    }

    /** 2-2: 최근 순, 실패 샘플은 빼고. */
    @GetMapping("/api/projects/{projectId}/uploads")
    public List<UploadResponse> list(@AuthenticationPrincipal AuthUser user, @PathVariable long projectId) {
        return uploadService.list(user.id(), projectId).stream().map(UploadResponse::summary).toList();
    }

    /** 2-3 */
    @GetMapping("/api/uploads/{uploadId}")
    public UploadResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable long uploadId) {
        return UploadResponse.detail(uploadService.get(user.id(), uploadId), objectMapper);
    }
}
