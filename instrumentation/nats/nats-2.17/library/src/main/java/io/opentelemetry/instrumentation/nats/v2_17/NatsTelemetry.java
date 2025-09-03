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
import java.io.IOException;

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

  Connection wrap(Connection connection) {
    return OpenTelemetryConnection.wrap(
        connection, producerInstrumenter, consumerProcessInstrumenter);
  }

  Options.Builder configure(Options.Builder options) {
    DispatcherFactory factory = options.build().getDispatcherFactory();

    if (factory == null) {
      factory = new DispatcherFactory();
    }

    return options.dispatcherFactory(
        new OpenTelemetryDispatcherFactory(factory, consumerProcessInstrumenter));
  }

  /** Returns a {@link Connection} with messaging spans instrumentation. */
  public Connection newConnection(Options options, ConnectionFactory<Options> connectionFactory)
      throws IOException, InterruptedException {
    return wrap(connectionFactory.create(configure(new Options.Builder(options)).build()));
  }

  /** Returns a {@link Connection} with messaging spans instrumentation. */
  public Connection newConnection(
      Options.Builder builder, ConnectionFactory<Options.Builder> connectionFactory)
      throws IOException, InterruptedException {
    return wrap(connectionFactory.create(configure(builder)));
  }

  public interface ConnectionFactory<T> {
    Connection create(T options) throws IOException, InterruptedException;
  }
}
