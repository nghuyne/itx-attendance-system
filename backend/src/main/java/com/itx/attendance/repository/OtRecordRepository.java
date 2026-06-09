package com.itx.attendance.repository;

import com.itx.attendance.domain.OtRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtRecordRepository extends JpaRepository<OtRecord, String> {

    Optional<OtRecord> findByAttendanceRecordId(String attendanceRecordId);

    void deleteByAttendanceRecordId(String attendanceRecordId);
}
