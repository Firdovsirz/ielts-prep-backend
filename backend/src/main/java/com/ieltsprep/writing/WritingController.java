package com.ieltsprep.writing;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/writing")
@Tag(name = "Writing")
public class WritingController {

    private final WritingService writing;

    public WritingController(WritingService writing) {
        this.writing = writing;
    }

    @GetMapping("/prompts")
    public List<WritingDtos.PromptSummary> prompts() {
        return writing.prompts();
    }

    @GetMapping("/topic-coverage")
    public Map<String, Integer> topicCoverage() {
        return writing.topicCoverage();
    }

    @PostMapping("/sessions")
    public WritingDtos.SessionView start(@Valid @RequestBody WritingDtos.StartRequest req) {
        return writing.start(req, null);
    }

    @GetMapping("/sessions/{id}")
    public WritingDtos.SessionView session(@PathVariable long id) {
        return writing.view(id);
    }

    @PostMapping("/sessions/{id}/submit")
    public WritingDtos.ResultView submit(@PathVariable long id, @RequestBody WritingDtos.SubmitRequest req) {
        return writing.submit(id, req);
    }

    @GetMapping("/sessions/{id}/result")
    public WritingDtos.ResultView result(@PathVariable long id) {
        return writing.result(id);
    }

    @PostMapping("/attempts/{id}/regrade")
    public WritingDtos.ResultView regrade(@PathVariable long id) {
        return writing.regrade(id);
    }
}
