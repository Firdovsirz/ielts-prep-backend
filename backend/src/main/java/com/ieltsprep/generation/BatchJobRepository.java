package com.ieltsprep.generation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchJobRepository extends JpaRepository<BatchJob, Long> {

    List<BatchJob> findByStatusOrderByIdAsc(String status);

    List<BatchJob> findTop20ByOrderByIdDesc();
}
