package com.ieltsprep.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "study_plan")
@Getter
@Setter
public class PlanTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate planDate;
    private String module;
    private String task;
    private String details;

    @Column(name = "task_type")
    private String action;

    private String variant;
    private int minutes;
    private int priority;
    private boolean done;
    private Instant doneAt;
    private String source;
    private LocalDate weekStart;
    private Instant createdAt;
}
