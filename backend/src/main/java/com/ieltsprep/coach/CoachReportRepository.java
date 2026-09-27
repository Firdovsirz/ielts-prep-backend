package com.ieltsprep.coach;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachReportRepository extends JpaRepository<CoachReport, Long> {

    List<CoachReport> findTop52ByOrderByCreatedAtDescIdDesc();

    Optional<CoachReport> findFirstByOrderByCreatedAtDescIdDesc();
}
