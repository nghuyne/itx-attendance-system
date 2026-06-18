package com.itx.attendance.repository;

import com.itx.attendance.domain.OfficeLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfficeLocationRepository extends JpaRepository<OfficeLocation, Long> {

    List<OfficeLocation> findAllByOrderByNameAsc();

    List<OfficeLocation> findByActiveTrue();
}
