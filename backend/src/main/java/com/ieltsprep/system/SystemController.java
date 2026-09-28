package com.ieltsprep.system;

import com.ieltsprep.claude.ApiKeyStore;
import com.ieltsprep.claude.ClaudeProperties;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.claude.ModelRoute;
import com.ieltsprep.claude.SpendGuard;
import com.ieltsprep.common.ApiException;
import com.ieltsprep.content.ItemRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System")
public class SystemController {

    public record Health(String status) {}

    public record ResetRequest(String confirm) {}

    public record InventoryRow(String module, String taskType, String status, long count) {}

    /**
     * @param apiKeySource APP (entered in Settings), ENV (ANTHROPIC_API_KEY in .env) or NONE
     * @param apiKeyHint   last four characters of the key in use, e.g. "…Ab12"
     */
    public record SystemStatus(
            SpendGuard.Status spend,
            Map<String, String> models,
            boolean apiKeyConfigured,
            String apiKeySource,
            @Schema(nullable = true) String apiKeyHint,
            List<InventoryRow> inventory) {}

    @Schema(name = "ApiKeyRequest")
    public record ApiKeyRequest(String apiKey) {}

    @Schema(name = "ApiKeyStatus")
    public record ApiKeyStatus(boolean configured, String source, @Schema(nullable = true) String hint) {}

    private final SpendGuard spendGuard;
    private final ClaudeProperties claude;
    private final ClaudeService claudeService;
    private final ApiKeyStore keys;
    private final ItemRepository items;
    private final ProgressResetService reset;

    public SystemController(SpendGuard spendGuard, ClaudeProperties claude, ClaudeService claudeService, ApiKeyStore keys,
            ItemRepository items, ProgressResetService reset) {
        this.spendGuard = spendGuard;
        this.claude = claude;
        this.claudeService = claudeService;
        this.keys = keys;
        this.items = items;
        this.reset = reset;
    }

    /** Deletes all practice history. The body must be {"confirm": "RESET"}. */
    @PostMapping("/reset-progress")
    public Map<String, Integer> resetProgress(@RequestBody ResetRequest req) {
        if (req == null || !"RESET".equals(req.confirm())) {
            throw ApiException.badRequest("Type RESET to confirm");
        }
        return reset.reset();
    }

    /**
     * Saves an Anthropic API key entered in the app (verified with Anthropic first). It takes effect immediately and
     * overrides ANTHROPIC_API_KEY from .env. The key itself is never returned.
     */
    @PutMapping("/api-key")
    public ApiKeyStatus saveApiKey(@RequestBody ApiKeyRequest req) {
        String key = req == null || req.apiKey() == null ? "" : req.apiKey().trim();
        if (!key.startsWith("sk-ant-") || key.length() < 30 || key.chars().anyMatch(Character::isWhitespace)) {
            throw ApiException.badRequest("That does not look like an Anthropic API key (it starts with sk-ant-).");
        }
        claudeService.verifyKey(key);
        keys.save(key);
        return apiKeyStatus();
    }

    /** Removes the key saved in the app; ANTHROPIC_API_KEY from .env (if any) applies again. */
    @DeleteMapping("/api-key")
    public ApiKeyStatus deleteApiKey() {
        keys.clear();
        return apiKeyStatus();
    }

    @GetMapping("/api-key")
    public ApiKeyStatus apiKeyStatus() {
        return new ApiKeyStatus(keys.present(), keys.source().name(), keys.hint());
    }

    @GetMapping("/health")
    public Health health() {
        return new Health("UP");
    }

    @GetMapping("/status")
    public SystemStatus status() {
        Map<String, String> models = new LinkedHashMap<>();
        for (ModelRoute route : ModelRoute.values()) {
            models.put(route.key(), claude.model(route));
        }
        List<InventoryRow> inventory = new ArrayList<>();
        for (Object[] row : items.inventory()) {
            inventory.add(new InventoryRow(String.valueOf(row[0]), String.valueOf(row[1]), String.valueOf(row[2]), (Long) row[3]));
        }
        return new SystemStatus(spendGuard.status(), models, keys.present(), keys.source().name(), keys.hint(), inventory);
    }

    @GetMapping("/spend")
    public SpendGuard.Status spend() {
        return spendGuard.status();
    }
}
