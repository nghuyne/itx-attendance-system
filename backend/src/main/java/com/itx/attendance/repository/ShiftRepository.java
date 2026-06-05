package com.itx.attendance.repository;

import com.itx.attendance.domain.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, String> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, String id);
}
