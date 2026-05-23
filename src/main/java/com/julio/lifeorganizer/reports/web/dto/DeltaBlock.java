package com.julio.lifeorganizer.reports.web.dto;

import java.math.BigDecimal;

/**
 * Difference between two scalar values. {@code percent} is null when the
 * previous value was zero (division would be undefined).
 */
public record DeltaBlock(BigDecimal absolute, BigDecimal percent) {
}
