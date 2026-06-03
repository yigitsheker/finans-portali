package com.finansportali.backend.dto.response.bond;

/** Result of a bond buy/sell: resulting position (null if fully sold) + the leg. */
public record BondTradeResult(
        BondPositionView position,
        BondPositionTransactionView transaction,
        String message
) {}
