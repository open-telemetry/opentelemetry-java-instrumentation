/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.internal.DbExecution;
import io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.shaded.io.r2dbc.proxy.ProxyConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

/** Entrypoint for instrumenting R2dbc. */
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

  private final Instrumenter<DbExecution, Void> instrumenter;

  R2dbcTelemetry(Instrumenter<DbExecution, Void> instrumenter) {
    this.instrumenter = instrumenter;
  }

  public ConnectionFactory wrapConnectionFactory(
      ConnectionFactory originalFactory, ConnectionFactoryOptions factoryOptions) {
    return ProxyConnectionFactory.builder(originalFactory)
        .listener(new TraceProxyListener(instrumenter, factoryOptions))
        .build();
  }
}
