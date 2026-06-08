package com.itx.attendance.repository;

import com.itx.attendance.domain.AttendanceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, String> {

    Optional<AttendanceRecord> findByEmployeeIdAndDate(String employeeId, LocalDate date);

    boolean existsByEmployeeIdAndDate(String employeeId, LocalDate date);

    Page<AttendanceRecord> findByEmployeeId(String employeeId, Pageable pageable);
}
