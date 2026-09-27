package com.ieltsprep.coach;

import com.ieltsprep.claude.ClaudeCall;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.ApiException;
import com.ieltsprep.common.Json;
import com.ieltsprep.plan.PlanService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Weekly coach reports: statistics for the last seven days, written up by Claude (coach-report prompt) or by the
 * rule-based fallback. The scheduled run (ielts.coach.cron, Monday 07:00 by default) also re-plans the new week.
 */
@Service
public class CoachService {

    private static final Logger log = LoggerFactory.getLogger(CoachService.class);

    private final CoachStatsService stats;
    private final CoachReportRepository reports;
    private final ClaudeService claude;
    private final PlanService plan;
    private final Clock clock;

    public CoachService(CoachStatsService stats, CoachReportRepository reports, ClaudeService claude, PlanService plan, Clock clock) {
        this.stats = stats;
        this.reports = reports;
        this.claude = claude;
        this.plan = plan;
        this.clock = clock;
    }

    @Scheduled(cron = "${ielts.coach.cron}")
    public void weekly() {
        try {
            generate("SCHEDULED", true);
            plan.regenerate(true, true);
        } catch (RuntimeException e) {
            log.error("Weekly coach run failed", e);
        }
    }

    /** SCHEDULED covers the seven days up to yesterday; MANUAL the seven days up to and including today. */
    public CoachDtos.ReportView generate(String trigger, boolean background) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = "SCHEDULED".equals(trigger) ? today.minusDays(1) : today;
        LocalDate start = end.minusDays(6);
        WeeklyStats s = stats.compute(start, end);
        CoachReportContent content = null;
        String by = "RULES";
        if (claude.isAvailable()) {
            try {
                ClaudeCall<CoachReportContent> call = ClaudeCall.of("coach-report",
                        Map.of("week_start", start.toString(), "week_end", end.toString(), "stats", Json.pretty(s)), CoachReportContent.class)
                        .ref("coach report " + start);
                content = claude.call(background ? call.inBackground() : call);
                by = "AI";
            } catch (RuntimeException e) {
                log.warn("Coach report via Claude failed, using the rule-based report: {}", e.getMessage());
            }
        }
        if (content == null) {
            content = CoachFallback.write(s);
        }
        CoachReport r = new CoachReport();
        r.setWeekStart(start);
        r.setWeekEnd(end);
        r.setReport(Json.write(content));
        r.setStats(Json.write(s));
        r.setTriggerType(trigger);
        r.setGeneratedBy(by);
        r.setCreatedAt(Instant.now(clock));
        return view(reports.save(r));
    }

    public List<CoachDtos.ReportView> list() {
        return reports.findTop52ByOrderByCreatedAtDescIdDesc().stream().map(CoachService::view).toList();
    }

    public CoachDtos.ReportView get(long id) {
        return view(reports.findById(id).orElseThrow(() -> ApiException.notFound("Coach report " + id)));
    }

    static CoachDtos.ReportView view(CoachReport r) {
        return new CoachDtos.ReportView(r.getId(), r.getWeekStart(), r.getWeekEnd(), r.getCreatedAt(), r.getTriggerType(), r.getGeneratedBy(),
                Json.read(r.getReport(), CoachReportContent.class), r.getStats() == null ? null : Json.read(r.getStats(), WeeklyStats.class));
    }
}
