package com.finansportali.backend.entity;

/**
 * Direction of a VİOP (futures) position. A user never holds both LONG and
 * SHORT on the same contract at once — net-position logic collapses opposing
 * sides (see ViopPositionService).
 */
public enum ViopDirection {
    LONG,
    SHORT
}
