package com.ieltsprep.mock;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mock")
@Tag(name = "Mock test")
public class MockController {

    private final MockService mock;

    public MockController(MockService mock) {
        this.mock = mock;
    }

    @GetMapping
    public List<MockDtos.Summary> list() {
        return mock.list();
    }

    /** Starts a full mock test (or resumes the one in progress). */
    @PostMapping
    public MockDtos.MockView start() {
        return mock.start();
    }

    @GetMapping("/{id}")
    public MockDtos.MockView get(@PathVariable long id) {
        return mock.get(id);
    }

    /** Creates the exam-mode session for the current paper and returns it as the stage's sessionId. */
    @PostMapping("/{id}/stage")
    public MockDtos.MockView startStage(@PathVariable long id) {
        return mock.startStage(id);
    }

    @PostMapping("/{id}/abandon")
    public MockDtos.MockView abandon(@PathVariable long id) {
        return mock.abandon(id);
    }
}
