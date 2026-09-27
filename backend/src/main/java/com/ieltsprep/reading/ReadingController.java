package com.ieltsprep.reading;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reading")
@Tag(name = "Reading")
public class ReadingController {

    private final ReadingService reading;

    public ReadingController(ReadingService reading) {
        this.reading = reading;
    }

    @GetMapping("/passages")
    public List<ReadingDtos.PassageSummary> passages() {
        return reading.passages();
    }

    @PostMapping("/sessions")
    public ReadingDtos.SessionView start(@Valid @RequestBody ReadingDtos.StartRequest req) {
        return reading.start(req, null);
    }

    @GetMapping("/sessions/{id}")
    public ReadingDtos.SessionView session(@PathVariable long id) {
        return reading.view(id);
    }

    @PostMapping("/sessions/{id}/submit")
    public ReadingDtos.ResultView submit(@PathVariable long id, @RequestBody ReadingDtos.SubmitRequest req) {
        return reading.submit(id, req);
    }

    @GetMapping("/sessions/{id}/result")
    public ReadingDtos.ResultView result(@PathVariable long id) {
        return reading.result(id);
    }
}
