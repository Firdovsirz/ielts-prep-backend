package com.ieltsprep.vocab;

/**
 * Receives words for the vocabulary deck from other modules (flagged in Reading/Listening, suggested as upgrades in
 * Writing/Speaking feedback). Implemented by the Vocabulary module.
 */
public interface VocabCapture {

    void capture(String word, String contextSentence, String source, String sourceRef);
}
