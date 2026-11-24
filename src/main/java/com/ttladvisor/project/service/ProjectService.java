package com.ttladvisor.project.service;

import com.ttladvisor.analysis.domain.enums.AnalysisStatus;
import com.ttladvisor.analysis.repository.AnalysisRepository;
import com.ttladvisor.global.error.ApiException;
import com.ttladvisor.global.error.ErrorCode;
import com.ttladvisor.project.domain.entity.Project;
import com.ttladvisor.project.repository.ProjectRepository;
import com.ttladvisor.project.repository.ProjectSummary;
import com.ttladvisor.upload.domain.enums.UploadStatus;
import com.ttladvisor.upload.repository.LogUploadRepository;
import com.ttladvisor.upload.repository.RequestLogJdbcRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    static final int REQUEST_LOG_DELETE_BATCH = 10_000;

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private static final EnumSet<UploadStatus> BUSY_UPLOAD = EnumSet.of(UploadStatus.UPLOADED, UploadStatus.PARSING);
    private static final EnumSet<AnalysisStatus> BUSY_ANALYSIS = EnumSet.of(AnalysisStatus.PENDING, AnalysisStatus.RUNNING);

    private final ProjectRepository projects;
    private final LogUploadRepository uploads;
    private final AnalysisRepository analyses;
    private final RequestLogJdbcRepository requestLogs;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, LogUploadRepository uploads, AnalysisRepository analyses,
                          RequestLogJdbcRepository requestLogs, Clock clock) {
        this.projects = projects;
        this.uploads = uploads;
        this.analyses = analyses;
        this.requestLogs = requestLogs;
        this.clock = clock;
    }

    @Transactional
    public Project create(String name) {
        // DB는 밀리초까지 저장하므로 응답과 저장 값이 같도록 미리 자른다
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        return projects.save(new Project(name.strip(), now));
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> list() {
        return projects.findAllSummaries();
    }

    @Transactional(readOnly = true)
    public ProjectSummary get(long projectId) {
        return projects.findSummaryById(projectId).orElseThrow(() -> notFound(projectId));
    }

    /**
     * 프로젝트와 하위 데이터를 모두 지운다.
     * 트랜잭션을 걸지 않는다: 요청 로그를 배치마다 커밋하며 지운 뒤, 프로젝트 행을 지우면
     * 나머지(정책·업로드·분석·결과)는 FK ON DELETE CASCADE로 함께 삭제된다.
     */
    public void delete(long projectId) {
        if (!projects.existsById(projectId)) {
            throw notFound(projectId);
        }
        if (uploads.existsByProjectIdAndStatusIn(projectId, BUSY_UPLOAD)
                || analyses.existsByUploadProjectIdAndStatusIn(projectId, BUSY_ANALYSIS)) {
            throw busy(projectId);
        }

        long deletedLogs = 0;
        for (Long uploadId : uploads.findIdsByProjectId(projectId)) {
            deletedLogs += requestLogs.deleteByUploadId(uploadId, REQUEST_LOG_DELETE_BATCH);
        }

        try {
            projects.deleteById(projectId);
        } catch (DataIntegrityViolationException e) {
            // 검사 이후 새 업로드가 요청 로그를 쌓기 시작한 경우. request_log FK에는 CASCADE가 없어 여기서 막힌다
            throw busy(projectId);
        }
        log.info("프로젝트 삭제: id={}, 요청 로그 {}건", projectId, deletedLogs);
    }

    private static ApiException notFound(long projectId) {
        return new ApiException(ErrorCode.PROJECT_NOT_FOUND, Map.of("projectId", projectId));
    }

    private static ApiException busy(long projectId) {
        return new ApiException(ErrorCode.RESOURCE_BUSY, Map.of("projectId", projectId));
    }
}
