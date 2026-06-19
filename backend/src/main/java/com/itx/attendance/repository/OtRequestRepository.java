package com.itx.attendance.repository;

import com.itx.attendance.domain.OtRequest;
import com.itx.attendance.domain.RequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtRequestRepository extends JpaRepository<OtRequest, String> {

    List<OtRequest> findByEmployeeId(String employeeId);

    List<OtRequest> findByStatus(RequestStatus status);

    List<OtRequest> findByEmployeeIdInAndStatus(List<String> employeeIds, RequestStatus status);

    boolean existsByEmployeeIdAndPlannedDateAndStatus(String employeeId, LocalDate plannedDate, RequestStatus status);

    long countByEmployeeIdAndStatus(String employeeId, RequestStatus status);

    Optional<OtRequest> findFirstByEmployeeIdAndPlannedDateAndStatus(String employeeId, LocalDate plannedDate, RequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM OtRequest r WHERE r.id = :id")
    Optional<OtRequest> findByIdForUpdate(@Param("id") String id);
}
