package com.ieltsprep.coach;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coach/reports")
@Tag(name = "Coach")
public class CoachController {

    private final CoachService coach;

    public CoachController(CoachService coach) {
        this.coach = coach;
    }

    @GetMapping
    public List<CoachDtos.ReportView> list() {
        return coach.list();
    }

    @GetMapping("/{id}")
    public CoachDtos.ReportView get(@PathVariable long id) {
        return coach.get(id);
    }

    /** Writes a report for the last seven days now (takes ~30 s with Claude; instant with the offline fallback). */
    @PostMapping
    public CoachDtos.ReportView generate() {
        return coach.generate("MANUAL", false);
    }
}
