/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import java.util.Set;
import java.util.function.Function;

public class CompletionStageFunction implements Function<Object, Object> {

  private final CassandraTelemetry telemetry;
  private final Set<EndPoint> programmaticContactPoints;

  public CompletionStageFunction(
      CassandraTelemetry telemetry, Set<EndPoint> programmaticContactPoints) {
    this.telemetry = telemetry;
    this.programmaticContactPoints = programmaticContactPoints;
  }

  @Override
  public Object apply(Object session) {
    if (session == null) {
      return null;
    }
    if (session.getClass().getName().endsWith("cassandra4.TracingCqlSession")) {
      return session;
    }
    return telemetry.wrap((CqlSession) session, programmaticContactPoints);
  }
}
