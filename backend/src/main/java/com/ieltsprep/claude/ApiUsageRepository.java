package com.ieltsprep.claude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApiUsageRepository extends JpaRepository<ApiUsage, Long> {

    @Query("select coalesce(sum(u.costUsd), 0) from ApiUsage u where u.createdAt >= ?1")
    BigDecimal sumCostSince(Instant since);

    @Query("select count(u) from ApiUsage u where u.createdAt >= ?1")
    long countSince(Instant since);

    List<ApiUsage> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant since);
}
