package com.finansportali.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketHistoryPoint(LocalDate day, BigDecimal close) {}
