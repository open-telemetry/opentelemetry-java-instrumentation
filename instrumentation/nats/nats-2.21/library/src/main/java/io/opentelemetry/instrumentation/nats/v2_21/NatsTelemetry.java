/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21;

import io.nats.client.Connection;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.nats.v2_21.internal.NatsRequest;

public final class NatsTelemetry {

  public static NatsTelemetry create(OpenTelemetry openTelemetry) {
    return new NatsTelemetryBuilder(openTelemetry).build();
  }

  public static NatsTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new NatsTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<NatsRequest, Void> producerInstrumenter;

  public NatsTelemetry(Instrumenter<NatsRequest, Void> producerInstrumenter) {
    this.producerInstrumenter = producerInstrumenter;
  }

  public OpenTelemetryConnection wrap(Connection connection) {
    return new OpenTelemetryConnection(connection, this.producerInstrumenter);
  }
}
