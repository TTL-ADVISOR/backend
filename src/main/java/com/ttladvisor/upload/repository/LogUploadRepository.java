package com.ttladvisor.upload.repository;

import com.ttladvisor.upload.domain.entity.LogUpload;
import com.ttladvisor.upload.domain.enums.UploadStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LogUploadRepository extends JpaRepository<LogUpload, Long> {

    boolean existsByProjectIdAndStatusIn(Long projectId, Collection<UploadStatus> statuses);

    @Query("select u.id from LogUpload u where u.project.id = :projectId")
    List<Long> findIdsByProjectId(@Param("projectId") Long projectId);

    /** 다른 사용자의 업로드는 없는 것과 같이 빈 값 (404). */
    @Query("select u from LogUpload u where u.id = :id and u.project.ownerId = :ownerId")
    Optional<LogUpload> findOwned(@Param("id") Long id, @Param("ownerId") Long ownerId);

    List<LogUpload> findByProjectIdOrderByCreatedAtDescIdDesc(Long projectId);

    @Query("select u.id from LogUpload u where u.status in :statuses")
    List<Long> findIdsByStatusIn(@Param("statuses") Collection<UploadStatus> statuses);
}
