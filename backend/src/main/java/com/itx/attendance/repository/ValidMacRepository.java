package com.itx.attendance.repository;

import com.itx.attendance.domain.ValidMac;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ValidMacRepository extends JpaRepository<ValidMac, Long> {
    List<ValidMac> findByActiveTrue();
    boolean existsByBssidAndActiveTrue(String bssid);
}
