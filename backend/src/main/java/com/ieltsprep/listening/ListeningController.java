package com.ieltsprep.listening;

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
@RequestMapping("/api/listening")
@Tag(name = "Listening")
public class ListeningController {

    private final ListeningService listening;

    public ListeningController(ListeningService listening) {
        this.listening = listening;
    }

    @GetMapping("/sections")
    public List<ListeningDtos.SectionSummary> sections() {
        return listening.sections();
    }

    @PostMapping("/sessions")
    public ListeningDtos.SessionView start(@Valid @RequestBody ListeningDtos.StartRequest req) {
        return listening.start(req, null);
    }

    @GetMapping("/sessions/{id}")
    public ListeningDtos.SessionView session(@PathVariable long id) {
        return listening.view(id);
    }

    @PostMapping("/sessions/{id}/submit")
    public ListeningDtos.ResultView submit(@PathVariable long id, @RequestBody ListeningDtos.SubmitRequest req) {
        return listening.submit(id, req);
    }

    @GetMapping("/sessions/{id}/result")
    public ListeningDtos.ResultView result(@PathVariable long id) {
        return listening.result(id);
    }
}
