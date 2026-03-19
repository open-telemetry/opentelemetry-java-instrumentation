/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.cameltest;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.camel.builder.RouteBuilder;

public final class CamelTestRouter extends RouteBuilder {
  private static final long DEFAULT_EXCHANGE_DELAY_MS = 50;
  private static final long MAXIMUM_EXCHANGE_DELAY_DELTA = 20;

  private static final Map<Long, String> BIRD_SPECIES =
      Map.of(
          1L, "Accipiter nisus",
          2L, "Pyrrhula pyrrhula",
          3L, "Coccothraustes coccothraustes",
          4L, "Picus viridis",
          5L, "Dendrocopos major",
          6L, "Aegithalos caudatus",
          7L, "Garrulus glandarius");

  @Override
  public void configure() {
    from("direct:bird-species")
        .routeId("Bird-Species-Route")
        .process(
            exchange -> {
              try {
                Thread.sleep(
                    DEFAULT_EXCHANGE_DELAY_MS
                        + ThreadLocalRandom.current().nextLong(MAXIMUM_EXCHANGE_DELAY_DELTA));
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
              }
              Long id = exchange.getMessage().getHeader("id", Long.class);
              exchange.getMessage().setBody(BIRD_SPECIES.get(id));
            });

    from("timer:test-request-timer?period=50")
        .routeId("Camel-Test-Route")
        .threads(5) // Needed for some thread pool metrics to appear
        .setHeader("birdSpeciesId", this::getRandomBirdSpeciesId)
        .setHeader("id", simple("${header.birdSpeciesId}"))
        .to("direct:bird-species")
        .log("${body}");
  }

  long getRandomBirdSpeciesId() {
    return ThreadLocalRandom.current().nextLong(1, BIRD_SPECIES.size() + 1L);
  }
}
