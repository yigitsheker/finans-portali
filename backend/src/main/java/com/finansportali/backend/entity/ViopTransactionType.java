package com.finansportali.backend.entity;

/**
 * VİOP transaction legs. A single user action can decompose into multiple legs
 * (e.g. flipping a 3-LONG position with a 5-contract short = 3×LONG_CLOSE +
 * 2×SHORT_OPEN). PARTIAL_CLOSE marks a close that left contracts still open.
 */
public enum ViopTransactionType {
    LONG_OPEN,
    LONG_CLOSE,
    SHORT_OPEN,
    SHORT_CLOSE,
    PARTIAL_CLOSE,
    EXPIRE
}
