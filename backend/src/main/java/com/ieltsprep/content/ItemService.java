package com.ieltsprep.content;

import com.ieltsprep.common.ApiException;
import com.ieltsprep.settings.SettingsService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serves verified items (never pending/failed ones) and tracks how often each has been used. */
@Service
public class ItemService {

    private final ItemRepository items;
    private final SettingsService settings;
    private final Clock clock;

    public ItemService(ItemRepository items, SettingsService settings, Clock clock) {
        this.items = items;
        this.settings = settings;
        this.clock = clock;
    }

    public Item get(long id) {
        return items.findById(id).orElseThrow(() -> ApiException.notFound("Item " + id));
    }

    public Optional<Item> peekNext(TaskType type, String variant) {
        return items.findServable(type, variant, null, PageRequest.of(0, 1)).stream().findFirst();
    }

    /** Picks the next item for a practice session and marks it served. */
    @Transactional
    public Item takeNext(TaskType type, String variant) {
        Item item = peekNext(type, variant).or(() -> variant == null ? Optional.empty() : peekNext(type, null))
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "NO_CONTENT",
                        "No verified " + type + (variant == null ? "" : " (" + variant + ")")
                                + " items yet. Seed content loads on first start; with an API key the buffer tops up automatically."));
        markServed(item);
        return item;
    }

    @Transactional
    public Item take(long id) {
        Item item = get(id);
        if (item.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw ApiException.badRequest("Item " + id + " is not verified");
        }
        markServed(item);
        return item;
    }

    public List<Item> list(TaskType type) {
        return items.findByTaskTypeAndVerificationStatusOrderByIdAsc(type, VerificationStatus.VERIFIED);
    }

    private void markServed(Item item) {
        item.setTimesServed(item.getTimesServed() + 1);
        item.setLastServedAt(Instant.now(clock));
        items.save(item);
    }

    public ExamType examType() {
        return settings.get().getExamType();
    }
}
