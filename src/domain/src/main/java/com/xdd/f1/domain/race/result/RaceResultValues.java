package com.xdd.f1.domain.race.result;

import com.xdd.f1.domain.error.DomainValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record RaceResultValues(
        List<RaceClassificationEntry> classification,
        Integer safetyCars,
        BigDecimal firstPlaceLead
) {
    public RaceResultValues {
        if (classification != null) {
            classification = List.copyOf(classification);
            if (classification.stream().map(RaceClassificationEntry::driver).distinct().count() != classification.size()) {
                throw new DomainValidationException("Race classification must not include duplicate drivers");
            }
        }
        if (safetyCars != null && safetyCars < 0) {
            throw new DomainValidationException("Safety cars count must not be negative");
        }
        if (firstPlaceLead != null) {
            if (firstPlaceLead.signum() < 0 || firstPlaceLead.compareTo(new BigDecimal("999999.999")) > 0) {
                throw new DomainValidationException("First place lead must be between 0 and 999999.999 seconds");
            }
            try {
                firstPlaceLead = firstPlaceLead.setScale(3, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException exception) {
                throw new DomainValidationException("First place lead must have at most three decimal places");
            }
        }
    }
}
