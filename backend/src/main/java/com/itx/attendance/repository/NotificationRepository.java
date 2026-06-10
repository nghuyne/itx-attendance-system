package com.itx.attendance.repository;

import com.itx.attendance.domain.Notification;
import com.itx.attendance.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByRecipientIdAndIsReadFalse(String recipientId);

    long countByRecipientIdAndIsReadFalse(String recipientId);

    boolean existsByRecipientIdAndTypeAndReferenceId(String recipientId, NotificationType type, String referenceId);

    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.id = :recipientId AND n.isRead = false")
    int markAllAsReadByRecipientId(@Param("recipientId") String recipientId);
}
