package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.infrastructure.persistence.race.RaceJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class RaceServiceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-02-03T04:05:06Z");

    private final RaceService service;
    private final RaceJpaRepository jpaRepository;

    @MockitoBean
    private Clock clock;

    @Autowired
    RaceServiceIntegrationTest(RaceService service, RaceJpaRepository jpaRepository) {
        this.service = service;
        this.jpaRepository = jpaRepository;
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    @Test
    void createsScheduledRaceAndLoadsItById() {
        var created = createRace(2027, 1, "Bahrain Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(7200));

        var loaded = service.getById(created.id());

        assertThat(loaded).isEqualTo(created);
        assertThat(loaded.status()).isEqualTo(RaceStatus.SCHEDULED);
    }

    @Test
    void returnsSeasonCalendarSortedByOpenTime() {
        var later = createRace(2027, 2, "Saudi Arabian Grand Prix", NOW.plusSeconds(7200), NOW.plusSeconds(10800));
        var earlier = createRace(2027, 1, "Bahrain Grand Prix", NOW.plusSeconds(3600), NOW.plusSeconds(5400));
        createRace(2026, 1, "Previous Season Grand Prix", NOW, NOW.plusSeconds(3600));

        var races = service.findAllBySeason(2027);

        assertThat(races).containsExactly(earlier, later);
    }

    @Test
    void opensClosesAndFinishesRace() {
        var race = createRace(2027, 1, "Bahrain Grand Prix", NOW, NOW.plusSeconds(3600));

        service.openRacePredictions(race.id());
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.OPEN);

        service.closeRacePredictions(race.id());
        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.CLOSED);

        service.finishRace(race.id());
        var finished = service.getById(race.id());
        assertThat(finished.status()).isEqualTo(RaceStatus.FINISHED);
        assertThat(service.findFinishedBySeason(2027)).containsExactly(finished);
    }

    @Test
    void rejectsInvalidStatusTransition() {
        var race = createRace(2027, 1, "Bahrain Grand Prix", NOW, NOW.plusSeconds(3600));

        assertThatThrownBy(() -> service.closeRacePredictions(race.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RaceStatus.OPEN.name());

        assertThat(service.getById(race.id()).status()).isEqualTo(RaceStatus.SCHEDULED);
    }

    @Test
    void processesOnlyDueTransitionsAndCanRunRepeatedly() {
        var toOpen = createRace(2027, 1, "Opening Grand Prix", NOW, NOW.plusSeconds(3600));
        var toClose = createRace(2027, 2, "Closing Grand Prix", NOW.minusSeconds(7200), NOW);
        var future = createRace(2027, 3, "Future Grand Prix", NOW.plusSeconds(1), NOW.plusSeconds(3600));
        service.openRacePredictions(toClose.id());

        service.processScheduledTransitions();
        service.processScheduledTransitions();

        assertThat(service.getById(toOpen.id()).status()).isEqualTo(RaceStatus.OPEN);
        assertThat(service.getById(toClose.id()).status()).isEqualTo(RaceStatus.CLOSED);
        assertThat(service.getById(future.id()).status()).isEqualTo(RaceStatus.SCHEDULED);
    }

    @Test
    void throwsWhenRaceDoesNotExist() {
        var id = new RaceId(UUID.randomUUID());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(RaceNotFoundException.class);
    }

    @Test
    void doesNothingIfRaceIsAlreadyOpened() {
        var toOpen = createRace(2027, 1, "Bahrain Grand Prix", NOW, NOW.plusSeconds(3600));
        service.openRacePredictions(toOpen.id());

        service.openRacePredictions(toOpen.id());

        assertThat(service.getById(toOpen.id()).status()).isEqualTo(RaceStatus.OPEN);
    }

    @Test
    void doesNothingIfRaceIsAlreadyClosed() {
        var toClose = createRace(2027, 1, "Bahrain Grand Prix", NOW, NOW.plusSeconds(3600));
        service.openRacePredictions(toClose.id());
        service.closeRacePredictions(toClose.id());

        service.closeRacePredictions(toClose.id());

        assertThat(service.getById(toClose.id()).status()).isEqualTo(RaceStatus.CLOSED);
    }

    private Race createRace(int season, int round, String name, Instant openAt, Instant closeAt) {
        return service.create(season, round, RaceType.RACE, name, openAt, closeAt);
    }
}
