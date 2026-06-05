package com.itx.attendance.repository;

import com.itx.attendance.domain.IpScope;
import com.itx.attendance.domain.ValidIp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ValidIpRepository extends JpaRepository<ValidIp, Long> {

    Page<ValidIp> findByActiveTrue(Pageable pageable);

    boolean existsByIpAddressAndScopeAndEmployeeIsNullAndActiveTrue(
            String ipAddress, IpScope scope);

    boolean existsByIpAddressAndScopeAndEmployeeIdAndActiveTrue(
            String ipAddress, IpScope scope, String employeeId);
}
