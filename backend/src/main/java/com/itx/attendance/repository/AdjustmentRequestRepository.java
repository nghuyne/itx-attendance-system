package com.itx.attendance.repository;

import com.itx.attendance.domain.AdjustmentRequest;
import com.itx.attendance.domain.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdjustmentRequestRepository extends JpaRepository<AdjustmentRequest, String> {

    Optional<AdjustmentRequest> findByAttendanceRecordIdAndStatus(String attendanceRecordId, RequestStatus status);

    List<AdjustmentRequest> findByEmployeeIdAndStatus(String employeeId, RequestStatus status);
}
