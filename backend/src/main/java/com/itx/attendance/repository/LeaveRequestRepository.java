package com.itx.attendance.repository;

import com.itx.attendance.domain.LeaveRequest;
import com.itx.attendance.domain.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeId(String employeeId);

    List<LeaveRequest> findByStatus(RequestStatus status);

    List<LeaveRequest> findByEmployeeIdInAndStatus(List<String> employeeIds, RequestStatus status);

    @Query("SELECT COUNT(lr) FROM LeaveRequest lr " +
           "WHERE lr.employee.id = :empId " +
           "AND lr.status IN (com.itx.attendance.domain.RequestStatus.PENDING, com.itx.attendance.domain.RequestStatus.APPROVED) " +
           "AND lr.startDate <= :endDate AND lr.endDate >= :startDate")
    long countOverlapping(@Param("empId") String employeeId,
                          @Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate);
}
