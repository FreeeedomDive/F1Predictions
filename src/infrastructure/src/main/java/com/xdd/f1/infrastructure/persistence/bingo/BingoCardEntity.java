package com.xdd.f1.infrastructure.persistence.bingo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "bingo_cards")
public class BingoCardEntity {
    @Id
    @Column(name = "id", nullable = false)
    UUID id;

    @Column(name = "season", nullable = false)
    int season;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "translations", nullable = false, columnDefinition = "jsonb")
    Map<String, BingoCardTranslationData> translations;

    @Column(name = "probability", nullable = false)
    String probability;

    @Column(name = "required_repeats", nullable = false)
    int requiredRepeats;

    @Column(name = "completed_repeats", nullable = false)
    int completedRepeats;

    protected BingoCardEntity() {
    }
}
