package com.ieltsprep.speaking;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/speaking")
@Tag(name = "Speaking")
public class SpeakingController {

    private final SpeakingService speaking;

    public SpeakingController(SpeakingService speaking) {
        this.speaking = speaking;
    }

    @PostMapping("/sessions")
    public SpeakingDtos.SessionView start(@Valid @RequestBody SpeakingDtos.StartRequest req) {
        return speaking.start(req, null);
    }

    @GetMapping("/sessions/{id}")
    public SpeakingDtos.SessionView session(@PathVariable long id) {
        return speaking.view(id);
    }

    /** The examiner's next turn (question, cue card, follow-up or closing). */
    @PostMapping("/sessions/{id}/examiner")
    public ExaminerTurn next(@PathVariable long id) {
        return speaking.next(id);
    }

    @PostMapping(value = "/sessions/{id}/responses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SpeakingDtos.ResponseView record(@PathVariable long id, @RequestParam int part, @RequestParam int questionIndex,
            @RequestParam String question, @RequestParam(required = false) String transcript,
            @RequestParam(required = false) Integer durationSeconds, @RequestPart(required = false) MultipartFile audio) {
        return speaking.record(id, part, questionIndex, question, transcript, durationSeconds, audio);
    }

    @PostMapping("/sessions/{id}/finish")
    public SpeakingDtos.ResultView finish(@PathVariable long id) {
        return speaking.finish(id);
    }

    @GetMapping("/sessions/{id}/result")
    public SpeakingDtos.ResultView result(@PathVariable long id) {
        return speaking.result(id);
    }

    @PostMapping("/attempts/{id}/regrade")
    public SpeakingDtos.ResultView regrade(@PathVariable long id) {
        return speaking.regrade(id);
    }

    @GetMapping("/responses/{id}/audio")
    public ResponseEntity<Resource> audio(@PathVariable long id) {
        Path file = speaking.audioFile(id);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(file.getFileName().toString()).orElse(MediaType.parseMediaType("audio/webm")))
                .body(new FileSystemResource(file));
    }
}
