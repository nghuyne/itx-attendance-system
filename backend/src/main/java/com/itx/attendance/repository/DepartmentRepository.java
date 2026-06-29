package com.itx.attendance.repository;

import com.itx.attendance.domain.Department;
import com.itx.attendance.dto.response.DepartmentDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByName(String name);

    @Query("SELECT new com.itx.attendance.dto.response.DepartmentDto(d.id, d.name, d.description, COUNT(u.id)) " +
           "FROM Department d LEFT JOIN User u ON u.department = d AND u.active = true " +
           "GROUP BY d.id, d.name, d.description")
    List<DepartmentDto> findAllWithActiveEmployeeCount();
}
