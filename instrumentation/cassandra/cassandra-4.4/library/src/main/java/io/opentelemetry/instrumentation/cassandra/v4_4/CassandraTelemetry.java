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
   * Returns a tracing-enabled {@link CqlSession} using the supplied contact points to derive the
   * session's logical Cassandra server target.
   *
   * <p>Use this overload when contact points were supplied directly to the session builder with
   * {@code addContactPoint} or {@code addContactPoints}. The driver does not expose those builder
   * contact points through the resulting {@link CqlSession}, so the instrumentation cannot derive
   * the logical server target from the session alone.
   *
   * <p>For sessions configured through a driver configuration file or {@link
   * com.datastax.oss.driver.api.core.config.DriverConfigLoader}, use {@link #wrap(CqlSession)}
   * instead.
   *
   * <p>The contact points are captured when the session is wrapped and are used only to derive
   * stable database server attributes. They do not change the session's connections or
   * configuration.
   *
   * <p>{@code contactPoints} must contain every contact point supplied to the builder. Do not pass
   * the current coordinator, discovered cluster nodes, or only a subset of the configured contact
   * points.
   *
   * @param session the configured session to wrap
   * @param contactPoints all contact points supplied directly to the session builder
   * @return a tracing-enabled session
   * @throws NullPointerException if {@code session} or {@code contactPoints} is {@code null}
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
