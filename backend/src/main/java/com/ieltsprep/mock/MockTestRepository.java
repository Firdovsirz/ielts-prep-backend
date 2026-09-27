package com.ieltsprep.mock;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MockTestRepository extends JpaRepository<MockTest, Long> {

    Optional<MockTest> findFirstByStatusOrderByStartedAtDesc(String status);

    List<MockTest> findTop30ByOrderByStartedAtDesc();
}
