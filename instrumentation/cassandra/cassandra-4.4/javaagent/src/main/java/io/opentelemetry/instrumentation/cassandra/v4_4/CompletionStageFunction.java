/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;

public class CompletionStageFunction implements Function<Object, Object> {

  private final CassandraTelemetry telemetry;
  @Nullable private final List<String> configuredContactPoints;
  @Nullable private final Set<EndPoint> programmaticContactPoints;

  public CompletionStageFunction(
      CassandraTelemetry telemetry,
      @Nullable List<String> configuredContactPoints,
      @Nullable Set<EndPoint> programmaticContactPoints) {
    this.telemetry = telemetry;
    this.configuredContactPoints = configuredContactPoints;
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
    return telemetry.wrap((CqlSession) session, configuredContactPoints, programmaticContactPoints);
  }
}
