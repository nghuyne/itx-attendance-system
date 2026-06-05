package com.itx.attendance.repository;

import com.itx.attendance.domain.Holiday;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    Page<Holiday> findByYear(int year, Pageable pageable);

    boolean existsByDate(LocalDate date);
}
