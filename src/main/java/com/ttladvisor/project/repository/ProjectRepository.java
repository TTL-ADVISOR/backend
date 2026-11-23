package com.ttladvisor.project.repository;

import com.ttladvisor.project.domain.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    String SUMMARY_SELECT = """
            select new com.ttladvisor.project.repository.ProjectSummary(
                p.id, p.name, p.createdAt,
                (select count(u) from LogUpload u where u.project = p),
                (select max(a.id) from Analysis a where a.upload.project = p))
            from Project p
            """;

    /** 목록: 최근 생성 순. 업로드 수와 최신 분석 ID를 서브쿼리로 한 번에 가져온다 (N+1 방지). */
    @Query(SUMMARY_SELECT + " order by p.createdAt desc, p.id desc")
    List<ProjectSummary> findAllSummaries();

    @Query(SUMMARY_SELECT + " where p.id = :id")
    Optional<ProjectSummary> findSummaryById(@Param("id") Long id);
}
