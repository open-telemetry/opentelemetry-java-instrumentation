/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4.internal;

import static java.util.Objects.requireNonNull;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import io.opentelemetry.instrumentation.cassandra.v4_4.CassandraTelemetry;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Back-channel between the {@code cassandra-4.4} library and the javaagent instrumentation, used to
 * wrap a session together with the contact points that were registered on its builder, without
 * exposing that overload as public API.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class CassandraTelemetryUtil {

  @Nullable private static volatile SessionWrapper sessionWrapper;

  public static CqlSession wrap(
      CassandraTelemetry telemetry, CqlSession session, Set<EndPoint> programmaticContactPoints) {
    // sessionWrapper is guaranteed non-null because CassandraTelemetry registers it during static
    // initialization, before an instance can be passed here
    return requireNonNull(sessionWrapper, "sessionWrapper")
        .wrap(telemetry, session, programmaticContactPoints);
  }

  public static void setSessionWrapper(SessionWrapper sessionWrapper) {
    CassandraTelemetryUtil.sessionWrapper = sessionWrapper;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  @FunctionalInterface
  public interface SessionWrapper {
    CqlSession wrap(
        CassandraTelemetry telemetry, CqlSession session, Set<EndPoint> programmaticContactPoints);
  }

  private CassandraTelemetryUtil() {}
}
