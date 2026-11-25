package com.ttladvisor.project.repository;

import com.ttladvisor.project.domain.entity.Project;
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

    /** 목록: 소유자의 프로젝트, 최근 생성 순. 업로드 수와 최신 분석 ID를 서브쿼리로 한 번에 가져온다 (N+1 방지). */
    @Query(SUMMARY_SELECT + " where p.ownerId = :ownerId order by p.createdAt desc, p.id desc")
    List<ProjectSummary> findAllSummaries(@Param("ownerId") Long ownerId);

    /** 다른 사용자의 프로젝트는 없는 것과 같이 빈 값을 돌려준다. */
    @Query(SUMMARY_SELECT + " where p.id = :id and p.ownerId = :ownerId")
    Optional<ProjectSummary> findSummaryById(@Param("id") Long id, @Param("ownerId") Long ownerId);

    boolean existsByIdAndOwnerId(Long id, Long ownerId);
}
