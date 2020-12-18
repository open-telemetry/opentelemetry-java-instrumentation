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
    BaggageBuilder applicationBaggageBuilder = Baggage.builder().setNoParent();
    agentBaggage.forEach(
        (key, value, metadata) ->
            applicationBaggageBuilder.put(
                key, value, BaggageEntryMetadata.create(metadata.getValue())));
    return applicationBaggageBuilder.build();
  }

  public static io.opentelemetry.api.baggage.Baggage toAgent(Baggage applicationBaggage) {
    io.opentelemetry.api.baggage.BaggageBuilder agentBaggageBuilder =
        io.opentelemetry.api.baggage.Baggage.builder().setNoParent();
    applicationBaggage.forEach(
        (key, value, metadata) ->
            agentBaggageBuilder.put(
                key,
                value,
                io.opentelemetry.api.baggage.BaggageEntryMetadata.create(metadata.getValue())));
    return agentBaggageBuilder.build();
  }

  private BaggageBridging() {}
}
