package com.itx.attendance.repository;

import com.itx.attendance.domain.OtRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtRecordRepository extends JpaRepository<OtRecord, String> {

    Optional<OtRecord> findByAttendanceRecordId(String attendanceRecordId);

    void deleteByAttendanceRecordId(String attendanceRecordId);

    @Query("SELECT o FROM OtRecord o WHERE o.date BETWEEN :from AND :to " +
           "AND (:employeeId IS NULL OR o.employee.id = :employeeId)")
    List<OtRecord> findForExport(
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        @Param("employeeId") String employeeId);
}
