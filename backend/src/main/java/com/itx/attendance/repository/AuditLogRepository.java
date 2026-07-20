package com.itx.attendance.repository;

import com.itx.attendance.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    // Plain-values native insert (no entity graph): safe to call from a thread with no
    // Hibernate Session of its own, e.g. the @Async email-sending callback in EmailService.
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(
        value = """
            INSERT INTO audit_logs (admin_id, target_table, target_id, field_changed, old_value, new_value, reason)
            VALUES (:adminId, :targetTable, :targetId, :fieldChanged, :oldValue, :newValue, :reason)
            """,
        nativeQuery = true
    )
    void insertPlain(
        @Param("adminId") String adminId,
        @Param("targetTable") String targetTable,
        @Param("targetId") String targetId,
        @Param("fieldChanged") String fieldChanged,
        @Param("oldValue") String oldValue,
        @Param("newValue") String newValue,
        @Param("reason") String reason);
}
