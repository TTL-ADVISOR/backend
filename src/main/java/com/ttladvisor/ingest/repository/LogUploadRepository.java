package com.ttladvisor.ingest.repository;

import com.ttladvisor.ingest.domain.LogUpload;
import com.ttladvisor.ingest.domain.UploadStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LogUploadRepository extends JpaRepository<LogUpload, Long> {

    boolean existsByProjectIdAndStatusIn(Long projectId, Collection<UploadStatus> statuses);

    @Query("select u.id from LogUpload u where u.project.id = :projectId")
    List<Long> findIdsByProjectId(@Param("projectId") Long projectId);
}
