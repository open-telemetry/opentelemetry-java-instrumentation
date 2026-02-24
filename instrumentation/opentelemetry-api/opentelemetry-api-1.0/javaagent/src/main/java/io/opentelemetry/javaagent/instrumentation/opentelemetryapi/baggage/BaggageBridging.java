/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.baggage;

import application.io.opentelemetry.api.baggage.Baggage;
import application.io.opentelemetry.api.baggage.BaggageBuilder;
import application.io.opentelemetry.api.baggage.BaggageEntryMetadata;

public final class BaggageBridging {

  public static Baggage toApplication(io.opentelemetry.api.baggage.Baggage agentBaggage) {
    BaggageBuilder applicationBaggageBuilder = Baggage.builder();
    agentBaggage.forEach(
        (key, entry) ->
            applicationBaggageBuilder.put(
                key,
                entry.getValue(),
                BaggageEntryMetadata.create(entry.getMetadata().getValue())));
    return applicationBaggageBuilder.build();
  }

  public static io.opentelemetry.api.baggage.Baggage toAgent(Baggage applicationBaggage) {
    io.opentelemetry.api.baggage.BaggageBuilder agentBaggageBuilder =
        io.opentelemetry.api.baggage.Baggage.builder();
    applicationBaggage.forEach(
        (key, entry) ->
            agentBaggageBuilder.put(
                key,
                entry.getValue(),
                io.opentelemetry.api.baggage.BaggageEntryMetadata.create(
                    entry.getMetadata().getValue())));
    return agentBaggageBuilder.build();
  }

  private BaggageBridging() {}
}
