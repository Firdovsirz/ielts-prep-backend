package com.ieltsprep.errorlog;

import java.util.Set;

/** Published after a graded attempt's errors are logged (the Grammar module pre-generates drills from it). */
public record ErrorsRecordedEvent(long attemptId, Set<String> grammarSubtypes) {}
