package com.itx.attendance.repository;

import com.itx.attendance.domain.AdjustmentRequest;
import com.itx.attendance.domain.RequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdjustmentRequestRepository extends JpaRepository<AdjustmentRequest, String> {

    Optional<AdjustmentRequest> findByAttendanceRecordIdAndStatus(String attendanceRecordId, RequestStatus status);

    List<AdjustmentRequest> findByEmployeeIdAndStatus(String employeeId, RequestStatus status);

    long countByEmployeeIdAndStatus(String employeeId, RequestStatus status);

    List<AdjustmentRequest> findByEmployeeIdInAndStatus(List<String> employeeIds, RequestStatus status);

    List<AdjustmentRequest> findByStatus(RequestStatus status);

    List<AdjustmentRequest> findByAttendanceRecordIdInAndStatus(List<String> recordIds, RequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AdjustmentRequest a WHERE a.id = :id")
    Optional<AdjustmentRequest> findByIdForUpdate(@Param("id") String id);
}
