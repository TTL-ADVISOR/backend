package com.ttladvisor.upload.dto.response;

import com.ttladvisor.upload.domain.entity.LogUpload;
import com.ttladvisor.upload.domain.enums.UploadStatus;

/** 2-1 업로드 접수 응답 (202). */
public record UploadAcceptedResponse(Long id, UploadStatus status) {

    public static UploadAcceptedResponse from(LogUpload upload) {
        return new UploadAcceptedResponse(upload.getId(), upload.getStatus());
    }
}
