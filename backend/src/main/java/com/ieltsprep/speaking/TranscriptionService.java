package com.ieltsprep.speaking;

import com.ieltsprep.common.Json;
import com.ieltsprep.config.AppProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Server-side transcription through a Whisper-compatible endpoint (OpenAI /v1/audio/transcriptions shape) when
 * SPEAKING_TRANSCRIPTION=WHISPER and WHISPER_URL are set. Otherwise the browser's Web Speech API transcript is used.
 */
@Service
public class TranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionService.class);

    private final AppProperties.Speaking config;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public TranscriptionService(AppProperties props) {
        this.config = props.speaking();
    }

    public boolean serverSide() {
        return config != null && "WHISPER".equalsIgnoreCase(config.transcription()) && config.whisperConfigured();
    }

    public String mode() {
        return serverSide() ? "WHISPER" : "BROWSER";
    }

    public Optional<String> transcribe(Path audio, String contentType) {
        if (!serverSide()) {
            return Optional.empty();
        }
        try {
            String boundary = "----ielts" + UUID.randomUUID();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n" + config.whisperModel() + "\r\n");
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"language\"\r\n\r\nen\r\n");
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + audio.getFileName()
                    + "\"\r\nContent-Type: " + (contentType == null ? "audio/webm" : contentType) + "\r\n\r\n");
            body.write(Files.readAllBytes(audio));
            write(body, "\r\n--" + boundary + "--\r\n");
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(config.whisperUrl()))
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
            if (config.whisperApiKey() != null && !config.whisperApiKey().isBlank()) {
                req.header("Authorization", "Bearer " + config.whisperApiKey());
            }
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                log.warn("Whisper transcription failed: {} {}", res.statusCode(), res.body());
                return Optional.empty();
            }
            return Optional.ofNullable(Json.tree(res.body()).path("text").asText(null));
        } catch (IOException e) {
            log.warn("Whisper transcription failed: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }
}
