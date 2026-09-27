package com.ieltsprep.vocab;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VocabCardRepository extends JpaRepository<VocabCard, Long> {

    Optional<VocabCard> findByWordIgnoreCase(String word);

    List<VocabCard> findBySuspendedFalseAndDueAtLessThanEqualOrderByDueAtAsc(Instant now);

    List<VocabCard> findBySuspendedFalseOrderByCreatedAtDesc();

    List<VocabCard> findByEnrichmentStatusOrderByIdAsc(String status);

    long countBySuspendedFalse();

    long countBySuspendedFalseAndDueAtLessThanEqual(Instant now);

    long countBySuspendedFalseAndIntervalDaysGreaterThanEqual(int days);

    long countBySuspendedFalseAndRepetitions(int repetitions);

    long countBySuspendedFalseAndAwlSublistIsNotNull();

    long countByCreatedAtBetween(Instant from, Instant to);
}
