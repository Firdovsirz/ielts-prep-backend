package com.ieltsprep.generation;

import com.ieltsprep.common.Json;
import java.util.Map;

final class ContentReviewSupport {

    private ContentReviewSupport() {}

    static Map<String, Object> vars(String itemKind, Object content, String checklist) {
        return Map.of("item_kind", itemKind, "content_json", Json.pretty(content), "checklist", checklist);
    }
}
