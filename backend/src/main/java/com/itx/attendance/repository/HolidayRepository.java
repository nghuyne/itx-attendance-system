package com.itx.attendance.repository;

import com.itx.attendance.domain.Holiday;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Set;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    Page<Holiday> findByYear(int year, Pageable pageable);

    boolean existsByDate(LocalDate date);

    @Query("SELECT h.date FROM Holiday h WHERE h.date BETWEEN :start AND :end")
    Set<LocalDate> findHolidayDatesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
