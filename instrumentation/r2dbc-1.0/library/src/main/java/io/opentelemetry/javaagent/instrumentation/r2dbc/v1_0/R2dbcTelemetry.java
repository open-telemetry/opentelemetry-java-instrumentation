/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import io.opentelemetry.api.OpenTelemetry;
import io.r2dbc.spi.ConnectionFactory;

/** Entrypoint for instrumenting Jetty client. */
public final class R2dbcTelemetry {

  /** Returns a new {@link R2dbcTelemetry} configured with the given {@link OpenTelemetry}. */
  public static R2dbcTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link R2dbcTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static R2dbcTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new R2dbcTelemetryBuilder(openTelemetry);
  }

  private final ConnectionFactory connectionFactory;

  R2dbcTelemetry(ConnectionFactory connectionFactory) {
    this.connectionFactory = connectionFactory;
  }

  public ConnectionFactory getConnectionFactory() {
    return connectionFactory;
  }
}
