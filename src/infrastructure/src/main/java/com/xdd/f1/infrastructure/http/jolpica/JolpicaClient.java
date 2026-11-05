package com.xdd.f1.infrastructure.http.jolpica;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

public interface JolpicaClient {
    @GetExchange("/{season}.json")
    JolpicaSeasonResponse getSeason(@PathVariable int season);
}
