/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.cameltest;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class CamelTestRouter extends RouteBuilder {

  public static final int BIRD_SPECIES_COUNT = 7;

  @Override
  public void configure() throws Exception {
    restConfiguration().host("localhost").port(8080);

    from("timer:test-request-timer?period=50")
        .routeId("Camel-Test-Route")
        .threads(5) // Needed for some thread pool metrics to appear
        .setHeader("birdSpeciesId", this::getRandomBirdSpeciesId)
        .to("rest:get:/birdspecies/{birdSpeciesId}")
        .log("${body}");
  }

  long getRandomBirdSpeciesId() {
    return (long) Math.floor(Math.random() * BIRD_SPECIES_COUNT) + 1;
  }
}
