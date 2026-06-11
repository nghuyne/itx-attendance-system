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

    @Override
    default void delete(AuditLog entity) {
        throw new UnsupportedOperationException("Audit logs are immutable");
    }

    @Override
    default void deleteById(Long id) {
        throw new UnsupportedOperationException("Audit logs are immutable");
    }

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException("Audit logs are immutable");
    }

    @Override
    default void deleteAll(Iterable<? extends AuditLog> entities) {
        throw new UnsupportedOperationException("Audit logs are immutable");
    }

    @Override
    default void deleteAllById(Iterable<? extends Long> ids) {
        throw new UnsupportedOperationException("Audit logs are immutable");
    }

    @Query(
        value = """
            SELECT al FROM AuditLog al
            JOIN FETCH al.admin
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
