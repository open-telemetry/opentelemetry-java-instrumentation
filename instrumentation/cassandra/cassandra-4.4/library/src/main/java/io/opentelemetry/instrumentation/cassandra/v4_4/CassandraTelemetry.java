/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static java.util.Objects.requireNonNull;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.cassandra.v4_4.internal.CassandraTelemetryUtil;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Set;

/** Entrypoint for instrumenting cassandra sessions. */
public final class CassandraTelemetry {

  static {
    CassandraTelemetryUtil.setSessionWrapper(CassandraTelemetry::wrap);
  }

  private final TracingCqlSession tracingCqlSession;

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

  CassandraTelemetry(Instrumenter<CassandraRequest, ExecutionInfo> instrumenter) {
    this.tracingCqlSession = new TracingCqlSession(instrumenter);
  }

  /**
   * Construct a new tracing-enabled CqlSession using the provided {@link CqlSession} instance.
   *
   * @param session An instance of CqlSession configured as desired.
   * @return a {@link TracingCqlSession}.
   */
  public CqlSession wrap(CqlSession session) {
    return tracingCqlSession.wrapSession(requireNonNull(session, "session"));
  }

  /**
   * Constructs a tracing-enabled {@link CqlSession} using the complete collection of contact points
   * configured for the provided session.
   *
   * <p>Use this overload when the session does not expose its original configuration. The contact
   * points must come from that configuration, not from the session's current topology or selected
   * coordinator.
   *
   * @param session an instance of CqlSession configured as desired
   * @param contactPoints the complete collection of contact points configured for the session
   * @return a {@link TracingCqlSession}
   */
  public CqlSession wrap(CqlSession session, Collection<InetSocketAddress> contactPoints) {
    return tracingCqlSession.wrapSession(
        requireNonNull(session, "session"), requireNonNull(contactPoints, "contactPoints"));
  }

  CqlSession wrap(CqlSession session, Set<EndPoint> programmaticContactPoints) {
    return tracingCqlSession.wrapSession(
        requireNonNull(session, "session"), programmaticContactPoints);
  }
}
