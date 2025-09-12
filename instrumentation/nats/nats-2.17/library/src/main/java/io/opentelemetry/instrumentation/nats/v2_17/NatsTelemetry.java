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

/** Entrypoint for instrumenting NATS clients. */
public final class NatsTelemetry {

  /** Returns a new {@link NatsTelemetry} configured with the given {@link OpenTelemetry}. */
  public static NatsTelemetry create(OpenTelemetry openTelemetry) {
    return new NatsTelemetryBuilder(openTelemetry).build();
  }

  /** Returns a new {@link NatsTelemetryBuilder} configured with the given {@link OpenTelemetry}. */
  public static NatsTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new NatsTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<NatsRequest, NatsRequest> producerInstrumenter;
  private final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter;

  NatsTelemetry(
      Instrumenter<NatsRequest, NatsRequest> producerInstrumenter,
      Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    this.producerInstrumenter = producerInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  /**
   * Returns a {@link Connection} with telemetry instrumentation.
   *
   * <p>This method should be used together with {@link #configure(Options.Builder)}. Consider using
   * {@link #newConnection(Options.Builder, ConnectionFactory)} or {@link #newConnection(Options,
   * ConnectionFactory)} instead.
   */
  public Connection wrap(Connection connection) {
    return OpenTelemetryConnection.wrap(
        connection, producerInstrumenter, consumerProcessInstrumenter);
  }

  /**
   * Returns a {@link Options.Builder} configured with telemetry instrumentation.
   *
   * <p>This method should be used together with {@link #wrap(Connection)}. Consider using {@link
   * #newConnection(Options.Builder, ConnectionFactory)} or {@link #newConnection(Options,
   * ConnectionFactory)} instead.
   */
  public Options.Builder configure(Options.Builder options) {
    DispatcherFactory factory = options.build().getDispatcherFactory();

    if (factory == null) {
      factory = new DispatcherFactory();
    }

    return options.dispatcherFactory(
        new OpenTelemetryDispatcherFactory(factory, consumerProcessInstrumenter));
  }

  /** Returns a {@link Connection} with telemetry instrumentation. */
  public Connection newConnection(Options options, ConnectionFactory<Options> connectionFactory)
      throws IOException, InterruptedException {
    return wrap(connectionFactory.create(configure(new Options.Builder(options)).build()));
  }

  /** Returns a {@link Connection} with telemetry instrumentation. */
  public Connection newConnection(
      Options.Builder builder, ConnectionFactory<Options.Builder> connectionFactory)
      throws IOException, InterruptedException {
    return wrap(connectionFactory.create(configure(builder)));
  }

  public interface ConnectionFactory<T> {
    Connection create(T options) throws IOException, InterruptedException;
  }
}
