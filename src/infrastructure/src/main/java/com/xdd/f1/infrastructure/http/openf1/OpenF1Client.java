package com.xdd.f1.infrastructure.http.openf1;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

import java.util.List;

public interface OpenF1Client {
    @GetExchange("/sessions")
    List<OpenF1Session> getSessions(@RequestParam("year") int year);

    @GetExchange("/starting_grid")
    List<OpenF1GridEntry> getStartingGrid(@RequestParam("session_key") int sessionKey);

    @GetExchange("/drivers")
    List<OpenF1Driver> getDrivers(@RequestParam("session_key") int sessionKey);
}
