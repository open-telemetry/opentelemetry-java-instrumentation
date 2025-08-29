/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import io.nats.client.Connection;
import io.nats.client.Options;
import io.nats.client.impl.DispatcherFactory;
import io.nats.client.impl.OpenTelemetryDispatcherFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsRequest;

public final class NatsTelemetry {

  public static NatsTelemetry create(OpenTelemetry openTelemetry) {
    return new NatsTelemetryBuilder(openTelemetry).build();
  }

  public static NatsTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new NatsTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<NatsRequest, NatsRequest> producerInstrumenter;
  private final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter;

  public NatsTelemetry(
      Instrumenter<NatsRequest, NatsRequest> producerInstrumenter,
      Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    this.producerInstrumenter = producerInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  /**
   * Returns a decorated {@link Connection} with messaging spans instrumentation. This will *not*
   * instrument the connection's main inbox by default. Use {@link #wrap(Options.Builder)}
   * beforehand to build an Options doing so.
   */
  public Connection wrap(Connection connection) {
    return OpenTelemetryConnection.wrap(
        connection, producerInstrumenter, consumerProcessInstrumenter);
  }

  /** Returns a {@link Options.Builder} with instrumented {@link DispatcherFactory}. */
  public Options.Builder wrap(Options.Builder options) {
    DispatcherFactory factory = options.build().getDispatcherFactory();

    if (factory == null) {
      factory = new DispatcherFactory();
    }

    return options.dispatcherFactory(
        new OpenTelemetryDispatcherFactory(factory, consumerProcessInstrumenter));
  }
}
