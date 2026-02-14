/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.baggage;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;

public final class BaggageBridging {

  public static application.io.opentelemetry.api.baggage.Baggage toApplication(
      Baggage agentBaggage) {
    application.io.opentelemetry.api.baggage.BaggageBuilder applicationBaggageBuilder =
        application.io.opentelemetry.api.baggage.Baggage.builder();
    agentBaggage.forEach(
        (key, entry) ->
            applicationBaggageBuilder.put(
                key,
                entry.getValue(),
                application.io.opentelemetry.api.baggage.BaggageEntryMetadata.create(
                    entry.getMetadata().getValue())));
    return applicationBaggageBuilder.build();
  }

  public static Baggage toAgent(
      application.io.opentelemetry.api.baggage.Baggage applicationBaggage) {
    BaggageBuilder agentBaggageBuilder = Baggage.builder();
    applicationBaggage.forEach(
        (key, entry) ->
            agentBaggageBuilder.put(
                key,
                entry.getValue(),
                BaggageEntryMetadata.create(entry.getMetadata().getValue())));
    return agentBaggageBuilder.build();
  }

  private BaggageBridging() {}
}
