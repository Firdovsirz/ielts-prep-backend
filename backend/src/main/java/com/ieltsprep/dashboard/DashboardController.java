package com.ieltsprep.dashboard;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Progress")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    public DashboardDtos.DashboardView dashboard() {
        return dashboard.dashboard();
    }

    @GetMapping("/history")
    public List<DashboardDtos.HistoryRow> history() {
        return dashboard.history();
    }
}
