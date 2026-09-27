package com.ieltsprep.plan;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanMetaRepository extends JpaRepository<PlanMeta, Long> {

    Optional<PlanMeta> findFirstByOrderByGeneratedAtDescIdDesc();
}
