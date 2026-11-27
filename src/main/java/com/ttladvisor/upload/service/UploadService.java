package com.ttladvisor.upload.service;

import com.ttladvisor.config.AsyncConfig;
import com.ttladvisor.global.error.ApiException;
import com.ttladvisor.global.error.ErrorCode;
import com.ttladvisor.project.repository.ProjectRepository;
import com.ttladvisor.project.service.ProjectService;
import com.ttladvisor.upload.domain.entity.LogUpload;
import com.ttladvisor.upload.repository.LogUploadRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 로그 업로드 접수와 조회 (API 2-1~2-3). 파싱은 {@link UploadParsingJob}이 백그라운드에서 한다. */
@Service
public class UploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jsonl", "log", "txt");
    private static final int MAX_FILENAME_LENGTH = 255;

    private final ProjectService projectService;
    private final ProjectRepository projects;
    private final LogUploadRepository uploads;
    private final UploadParsingJob parsingJob;
    private final TaskExecutor executor;
    private final Clock clock;

    public UploadService(ProjectService projectService, ProjectRepository projects, LogUploadRepository uploads,
                         UploadParsingJob parsingJob, @Qualifier(AsyncConfig.JOB_EXECUTOR) TaskExecutor executor,
                         Clock clock) {
        this.projectService = projectService;
        this.projects = projects;
        this.uploads = uploads;
        this.parsingJob = parsingJob;
        this.executor = executor;
        this.clock = clock;
    }

    /**
     * 파일을 임시 파일로 옮기고 업로드를 만든 뒤 파싱을 작업 큐에 넣는다.
     * 트랜잭션을 걸지 않는다: 업로드 행이 커밋된 뒤에 작업이 시작되어야 작업 스레드가 행을 볼 수 있다.
     * 요청이 끝나면 MultipartFile의 임시 파일은 지워지므로 우리 임시 파일로 먼저 옮긴다.
     */
    public LogUpload upload(long ownerId, long projectId, MultipartFile file) {
        projectService.checkOwner(ownerId, projectId);
        String filename = filenameOf(file);

        Path temp = copyToTemp(file);
        LogUpload upload;
        try {
            Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
            upload = uploads.save(new LogUpload(projects.getReferenceById(projectId), filename, now));
        } catch (RuntimeException e) {
            deleteQuietly(temp);
            throw e;
        }

        try {
            long uploadId = upload.getId();
            executor.execute(() -> parsingJob.run(uploadId, temp));
        } catch (TaskRejectedException e) {
            // 작업 큐가 가득 참 → 503. 처리되지 않을 업로드를 남기지 않는다
            uploads.deleteById(upload.getId());
            deleteQuietly(temp);
            throw e;
        }
        return upload;
    }

    @Transactional(readOnly = true)
    public List<LogUpload> list(long ownerId, long projectId) {
        projectService.checkOwner(ownerId, projectId);
        return uploads.findByProjectIdOrderByCreatedAtDescIdDesc(projectId);
    }

    @Transactional(readOnly = true)
    public LogUpload get(long ownerId, long uploadId) {
        return uploads.findOwned(uploadId, ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.UPLOAD_NOT_FOUND, Map.of("uploadId", uploadId)));
    }

    /** 경로를 뗀 파일 이름. 확장자가 .jsonl/.log/.txt가 아니면 415 (내용 검사는 파싱 단계에서). */
    static String filenameOf(MultipartFile file) {
        String original = file.getOriginalFilename();
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FORMAT,
                    "확장자가 .jsonl, .log, .txt인 파일만 올릴 수 있습니다.", Map.of("filename", name));
        }
        return name.length() <= MAX_FILENAME_LENGTH ? name : name.substring(name.length() - MAX_FILENAME_LENGTH);
    }

    private static Path copyToTemp(MultipartFile file) {
        try {
            Path temp = Files.createTempFile("ttl-upload-", ".jsonl");
            file.transferTo(temp);
            return temp;
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 파일 저장 실패", e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 임시 디렉터리라 남아도 OS가 정리한다
        }
    }
}
