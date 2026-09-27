package com.ieltsprep.generation;

import com.ieltsprep.content.TaskType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class BlueprintRegistry {

    private final Map<TaskType, Blueprint> blueprints = new EnumMap<>(TaskType.class);

    public BlueprintRegistry(List<Blueprint> all) {
        all.forEach(b -> blueprints.put(b.type(), b));
    }

    public Blueprint get(TaskType type) {
        return find(type).orElseThrow(() -> new IllegalArgumentException("No generator for " + type));
    }

    public Optional<Blueprint> find(TaskType type) {
        return Optional.ofNullable(blueprints.get(type));
    }

    public List<Blueprint> all() {
        return List.copyOf(blueprints.values());
    }
}
