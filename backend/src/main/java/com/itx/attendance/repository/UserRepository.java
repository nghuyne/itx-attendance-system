package com.itx.attendance.repository;

import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    List<User> findByRole(UserRole role);

    List<User> findByLeaderId(String leaderId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long countByShiftId(String shiftId);
}
