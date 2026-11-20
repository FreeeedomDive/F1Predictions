package com.xdd.f1.domain.championship;

import com.xdd.f1.domain.error.DomainValidationException;

public enum ChampionshipRulesVersion {
    V2026("championship-v2026"),
    V2027("championship-v2027");

    private final String value;

    ChampionshipRulesVersion(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ChampionshipRulesVersion fromValue(String value) {
        for (var version : values()) {
            if (version.value.equals(value)) {
                return version;
            }
        }
        throw new DomainValidationException("Championship rules are not defined for version " + value);
    }
}
