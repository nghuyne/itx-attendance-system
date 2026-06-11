package com.itx.attendance.repository;

import com.itx.attendance.domain.ExceptionRequest;
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
public interface ExceptionRequestRepository extends JpaRepository<ExceptionRequest, String> {

    Optional<ExceptionRequest> findByAttendanceRecordIdAndStatus(String attendanceRecordId, RequestStatus status);

    List<ExceptionRequest> findByEmployeeIdAndStatus(String employeeId, RequestStatus status);

    long countByEmployeeIdAndStatus(String employeeId, RequestStatus status);

    List<ExceptionRequest> findByEmployeeIdInAndStatus(List<String> employeeIds, RequestStatus status);

    List<ExceptionRequest> findByStatus(RequestStatus status);

    List<ExceptionRequest> findByAttendanceRecordIdInAndStatus(List<String> recordIds, RequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ExceptionRequest e WHERE e.id = :id")
    Optional<ExceptionRequest> findByIdForUpdate(@Param("id") String id);
}
