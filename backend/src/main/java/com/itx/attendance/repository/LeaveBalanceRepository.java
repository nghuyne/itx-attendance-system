package com.itx.attendance.repository;

import com.itx.attendance.domain.LeaveBalance;
import com.itx.attendance.domain.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    List<LeaveBalance> findByEmployeeIdAndYear(String employeeId, int year);

    Optional<LeaveBalance> findByEmployeeIdAndYearAndLeaveType(String employeeId, int year, LeaveType leaveType);
}
