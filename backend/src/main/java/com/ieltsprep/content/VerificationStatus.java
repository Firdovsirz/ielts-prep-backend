package com.ieltsprep.content;

public enum VerificationStatus {
    /** Generated, not yet checked by the independent verification pass. Never served. */
    PENDING,
    /** Passed structural validation and the blind verification pass. */
    VERIFIED,
    /** Failed verification after the maximum number of regenerations. Never served. */
    FAILED
}
