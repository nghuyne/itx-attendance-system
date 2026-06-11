package com.itx.attendance.repository;

import com.itx.attendance.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(
        value = """
            SELECT al FROM AuditLog al
            WHERE (:adminId IS NULL OR al.admin.id = :adminId)
            AND (:targetTable IS NULL OR al.targetTable = :targetTable)
            AND (:fromDate IS NULL OR al.createdAt >= :fromDate)
            AND (:toDate IS NULL OR al.createdAt <= :toDate)
            """,
        countQuery = """
            SELECT COUNT(al) FROM AuditLog al
            WHERE (:adminId IS NULL OR al.admin.id = :adminId)
            AND (:targetTable IS NULL OR al.targetTable = :targetTable)
            AND (:fromDate IS NULL OR al.createdAt >= :fromDate)
            AND (:toDate IS NULL OR al.createdAt <= :toDate)
            """
    )
    Page<AuditLog> findByFilters(
        @Param("adminId") String adminId,
        @Param("targetTable") String targetTable,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        Pageable pageable);
}
