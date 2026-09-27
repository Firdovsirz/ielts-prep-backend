package com.ieltsprep.claude;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.batches.BatchCreateParams;
import java.util.List;

/** A fully rendered request, usable for an interactive (streaming) call or as a Message Batches entry. */
public record PreparedRequest(
        String purpose,
        String model,
        long maxTokens,
        List<TextBlockParam> system,
        List<MessageParam> messages,
        OutputConfig outputConfig) {

    public MessageCreateParams toMessageParams() {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .systemOfTextBlockParams(system)
                .messages(messages)
                .outputConfig(outputConfig)
                .build();
    }

    public BatchCreateParams.Request toBatchRequest(String customId) {
        return BatchCreateParams.Request.builder()
                .customId(customId)
                .params(BatchCreateParams.Request.Params.builder()
                        .model(model)
                        .maxTokens(maxTokens)
                        .systemOfTextBlockParams(system)
                        .messages(messages)
                        .outputConfig(outputConfig)
                        .build())
                .build();
    }
}
