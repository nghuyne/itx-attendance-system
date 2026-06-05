package com.itx.attendance.repository;

import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void findByUsername_returnsUser_whenExists() {
        userRepository.save(User.builder()
            .username("admin").email("admin@itx.local")
            .passwordHash("$2a$10$test-hash").fullName("System Administrator")
            .role(UserRole.ADMIN).build());

        Optional<User> found = userRepository.findByUsername("admin");

        assertTrue(found.isPresent());
        assertEquals(UserRole.ADMIN, found.get().getRole());
        assertEquals("admin@itx.local", found.get().getEmail());
    }

    @Test
    void findByUsername_returnsEmpty_whenNotExists() {
        Optional<User> found = userRepository.findByUsername("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void leaderRelationship_isPersisted_andLoadable() {
        User leader = userRepository.save(User.builder()
            .username("leader1").email("leader1@itx.local")
            .passwordHash("hash").fullName("Test Leader")
            .role(UserRole.LEADER).build());

        userRepository.save(User.builder()
            .username("emp1").email("emp1@itx.local")
            .passwordHash("hash").fullName("Test Employee")
            .role(UserRole.EMPLOYEE).leader(leader).build());

        Optional<User> found = userRepository.findByUsername("emp1");
        assertTrue(found.isPresent());
        assertNotNull(found.get().getLeader());
        assertEquals("leader1", found.get().getLeader().getUsername());
    }

    @Test
    void adminUser_hasNullLeader() {
        userRepository.save(User.builder()
            .username("admin2").email("admin2@itx.local")
            .passwordHash("hash").fullName("Admin").role(UserRole.ADMIN).build());

        Optional<User> found = userRepository.findByUsername("admin2");
        assertTrue(found.isPresent());
        assertNull(found.get().getLeader());
    }

    @Test
    void findByRole_returnsCorrectUsers() {
        userRepository.save(User.builder().username("e1").email("e1@t.com")
            .passwordHash("h").fullName("E1").role(UserRole.EMPLOYEE).build());
        userRepository.save(User.builder().username("e2").email("e2@t.com")
            .passwordHash("h").fullName("E2").role(UserRole.EMPLOYEE).build());
        userRepository.save(User.builder().username("l1").email("l1@t.com")
            .passwordHash("h").fullName("L1").role(UserRole.LEADER).build());

        List<User> employees = userRepository.findByRole(UserRole.EMPLOYEE);
        assertEquals(2, employees.size());
    }
}
