package com.ttladvisor.upload.service;

import com.ttladvisor.upload.domain.enums.UploadStatus;
import com.ttladvisor.upload.repository.LogUploadRepository;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버가 파싱 도중 꺼지면 업로드가 UPLOADED·PARSING에 영원히 남는다 (임시 파일과 작업 큐는 사라졌다).
 * 시작할 때 이런 업로드를 FAILED로 바꾸고 적재된 행을 지운다. 그대로 두면 프로젝트도 "작업 중"이라 삭제할 수 없다.
 * v1은 서버 한 대를 가정한다. 여러 대로 늘리면 다른 서버가 처리 중인 업로드를 건드리지 않도록 소유 서버를 기록해야 한다.
 */
@Component
public class UploadRecovery {

    static final String REASON_INTERRUPTED = "서버가 다시 시작되어 처리가 중단되었습니다. 다시 업로드해 주세요";
    private static final Logger log = LoggerFactory.getLogger(UploadRecovery.class);

    private final LogUploadRepository uploads;
    private final UploadParsingJob parsingJob;

    public UploadRecovery(LogUploadRepository uploads, UploadParsingJob parsingJob) {
        this.uploads = uploads;
        this.parsingJob = parsingJob;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedUploads() {
        List<Long> interrupted = uploads.findIdsByStatusIn(EnumSet.of(UploadStatus.UPLOADED, UploadStatus.PARSING));
        for (Long uploadId : interrupted) {
            parsingJob.fail(uploadId, REASON_INTERRUPTED, null);
        }
        if (!interrupted.isEmpty()) {
            log.warn("중단된 업로드 {}건을 실패로 처리: {}", interrupted.size(), interrupted);
        }
    }
}
