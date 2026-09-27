package com.ieltsprep.plan;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plan")
@Tag(name = "Study plan")
public class PlanController {

    private final PlanService plan;

    public PlanController(PlanService plan) {
        this.plan = plan;
    }

    /** The next seven days (generated on first access, ticked off as sessions are completed). */
    @GetMapping
    public PlanDtos.PlanView plan() {
        return plan.view();
    }

    /** Rebuilds the week from the latest results; ai=true lets Claude personalise it (when an API key is set). */
    @PostMapping("/regenerate")
    public PlanDtos.PlanView regenerate(@RequestParam(defaultValue = "true") boolean ai) {
        return plan.regenerate(ai);
    }

    @PostMapping("/tasks/{id}/toggle")
    public PlanDtos.TaskView toggle(@PathVariable long id, @RequestBody PlanDtos.ToggleRequest req) {
        return plan.toggle(id, req.done());
    }
}
