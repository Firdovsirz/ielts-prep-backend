package com.ieltsprep.vocab;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Placeholder until the Vocabulary module registers the real deck. */
@Component
@ConditionalOnMissingBean(value = VocabCapture.class, ignored = NoopVocabCapture.class)
class NoopVocabCapture implements VocabCapture {

    @Override
    public void capture(String word, String contextSentence, String source, String sourceRef) {
        // no-op
    }
}
