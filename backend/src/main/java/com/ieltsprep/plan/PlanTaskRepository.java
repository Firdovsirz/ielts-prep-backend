package com.ieltsprep.plan;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanTaskRepository extends JpaRepository<PlanTask, Long> {

    List<PlanTask> findByPlanDateBetweenOrderByPlanDateAscPriorityAscIdAsc(LocalDate from, LocalDate to);

    List<PlanTask> findByPlanDateGreaterThanEqual(LocalDate from);

    boolean existsByPlanDate(LocalDate date);
}
