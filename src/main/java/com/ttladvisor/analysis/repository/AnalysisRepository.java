package com.ttladvisor.analysis.repository;

import com.ttladvisor.analysis.domain.Analysis;
import com.ttladvisor.analysis.domain.AnalysisStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    boolean existsByUploadProjectIdAndStatusIn(Long projectId, Collection<AnalysisStatus> statuses);
}
