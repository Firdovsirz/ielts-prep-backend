package com.ieltsprep.system;

import com.ieltsprep.claude.ClaudeProperties;
import com.ieltsprep.claude.ModelRoute;
import com.ieltsprep.claude.SpendGuard;
import com.ieltsprep.content.ItemRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System")
public class SystemController {

    public record Health(String status) {}

    public record InventoryRow(String module, String taskType, String status, long count) {}

    public record SystemStatus(
            SpendGuard.Status spend,
            Map<String, String> models,
            boolean apiKeyConfigured,
            List<InventoryRow> inventory) {}

    private final SpendGuard spendGuard;
    private final ClaudeProperties claude;
    private final ItemRepository items;

    public SystemController(SpendGuard spendGuard, ClaudeProperties claude, ItemRepository items) {
        this.spendGuard = spendGuard;
        this.claude = claude;
        this.items = items;
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
        return new SystemStatus(spendGuard.status(), models, claude.hasApiKey(), inventory);
    }

    @GetMapping("/spend")
    public SpendGuard.Status spend() {
        return spendGuard.status();
    }
}
