/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.cassandra.CassandraRequest;

public final class CassandraTelemetry
    extends io.opentelemetry.instrumentation.cassandra.CassandraTelemetry {

  /** Returns a new {@link CassandraTelemetry} configured with the given {@link OpenTelemetry}. */
  public static CassandraTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link CassandraTelemetryBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static CassandraTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new CassandraTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<CassandraRequest, ExecutionInfo> instrumenter;

  CassandraTelemetry(Instrumenter<CassandraRequest, ExecutionInfo> instrumenter) {
    super(instrumenter);
    this.instrumenter = instrumenter;
  }

  @Override
  public CqlSession wrap(CqlSession session) {
    io.opentelemetry.instrumentation.cassandra.TracingCqlSession originalTracingCqlSession =
        new io.opentelemetry.instrumentation.cassandra.TracingCqlSession(session, instrumenter);
    return new TracingCqlSession(originalTracingCqlSession);
  }
}
