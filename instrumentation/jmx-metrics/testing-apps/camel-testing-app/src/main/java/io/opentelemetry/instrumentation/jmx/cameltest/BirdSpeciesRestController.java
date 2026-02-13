/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.cameltest;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BirdSpeciesRestController {
  private final Map<Long, String> birdSpecies = new HashMap<>();

  public BirdSpeciesRestController() {
    birdSpecies.put(1L, "Accipiter nisus");
    birdSpecies.put(2L, "Pyrrhula pyrrhula");
    birdSpecies.put(3L, "Coccothraustes coccothraustes");
    birdSpecies.put(4L, "Picus viridis");
    birdSpecies.put(5L, "Dendrocopos major");
    birdSpecies.put(6L, "Aegithalos caudatus");
    birdSpecies.put(7L, "Garrulus glandarius");
  }

  @GetMapping("/birdspecies/{id}")
  public String getBirdSpecies(@PathVariable Long id) throws InterruptedException {
    Thread.sleep(10 + (long) (Math.random() * 100.0)); // A simulated backend delay
    return birdSpecies.get(id);
  }
}
