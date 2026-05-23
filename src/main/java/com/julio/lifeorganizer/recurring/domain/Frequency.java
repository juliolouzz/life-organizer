package com.julio.lifeorganizer.recurring.domain;

import java.time.LocalDate;

public enum Frequency {
    DAILY {
        @Override public LocalDate advance(LocalDate d) { return d.plusDays(1); }
    },
    WEEKLY {
        @Override public LocalDate advance(LocalDate d) { return d.plusWeeks(1); }
    },
    MONTHLY {
        @Override public LocalDate advance(LocalDate d) { return d.plusMonths(1); }
    },
    YEARLY {
        @Override public LocalDate advance(LocalDate d) { return d.plusYears(1); }
    };

    public abstract LocalDate advance(LocalDate from);
}
