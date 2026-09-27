package com.ieltsprep.errorlog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErrorEntryRepository extends JpaRepository<ErrorEntry, Long> {

    List<ErrorEntry> findByAttemptId(Long attemptId);

    List<ErrorEntry> findAllByOrderByCreatedAtDesc();

    List<ErrorEntry> findBySubtypeAndResolvedAtIsNull(String subtype);

    List<ErrorEntry> findBySubtypeOrderByCreatedAtDesc(String subtype);

    List<ErrorEntry> findByTypeOrderByCreatedAtDesc(String type);

    long countByCreatedAtBetween(java.time.Instant from, java.time.Instant to);

    long countByResolvedAtBetween(java.time.Instant from, java.time.Instant to);
}
