package com.ieltsprep.content;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findBySeedKey(String seedKey);

    List<Item> findByTaskTypeAndVerificationStatusOrderByIdAsc(TaskType taskType, VerificationStatus status);

    List<Item> findByTaskTypeAndVariantAndVerificationStatusOrderByIdAsc(TaskType taskType, String variant,
            VerificationStatus status);

    long countByTaskTypeAndVerificationStatus(TaskType taskType, VerificationStatus status);

    long countBySourceUrl(String sourceUrl);

    @Query("select i.title from Item i where i.taskType = ?1 order by i.id desc")
    List<String> recentTitles(TaskType taskType, Pageable page);

    @Query("""
            select count(i) from Item i where i.taskType = ?1 and (?2 is null or i.variant = ?2)
              and i.verificationStatus = com.ieltsprep.content.VerificationStatus.VERIFIED and i.timesServed = 0
            """)
    long countUnserved(TaskType taskType, String variant);

    /** Next item to serve: unserved first, then least-served/least-recently-served. */
    @Query("""
            select i from Item i where i.taskType = ?1 and (?2 is null or i.variant = ?2)
              and (?3 is null or i.examType = ?3 or i.examType = com.ieltsprep.content.ExamType.BOTH)
              and i.verificationStatus = com.ieltsprep.content.VerificationStatus.VERIFIED
            order by i.timesServed asc, i.lastServedAt asc nulls first, i.id asc
            """)
    List<Item> findServable(TaskType taskType, String variant, ExamType examType, Pageable page);

    @Query("select i.module, i.taskType, i.verificationStatus, count(i) from Item i group by i.module, i.taskType, i.verificationStatus")
    List<Object[]> inventory();
}
