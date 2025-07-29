/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;

/** Entrypoint for instrumenting Lettuce or clients. */
public final class LettuceTelemetry {

  public static final String INSTRUMENTATION_NAME = "io.opentelemetry.lettuce-5.1";

  /** Returns a new {@link LettuceTelemetry} configured with the given {@link OpenTelemetry}. */
  public static LettuceTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link LettuceTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static LettuceTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new LettuceTelemetryBuilder(openTelemetry);
  }

  private final Tracer tracer;
  private final RedisCommandSanitizer sanitizer;
  private final OperationListener metrics;

  LettuceTelemetry(
      OpenTelemetry openTelemetry,
      boolean statementSanitizationEnabled,
      OperationListener metrics) {
    this.metrics = metrics;
    TracerBuilder tracerBuilder = openTelemetry.tracerBuilder(INSTRUMENTATION_NAME);
    String version = EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME);
    if (version != null) {
      tracerBuilder.setInstrumentationVersion(version);
    }
    tracer = tracerBuilder.build();
    sanitizer = RedisCommandSanitizer.create(statementSanitizationEnabled);
  }

  /**
   * Returns a new {@link Tracing} which can be used with methods like {@link
   * io.lettuce.core.resource.ClientResources.Builder#tracing(Tracing)}.
   */
  public Tracing newTracing() {
    return new OpenTelemetryTracing(tracer, sanitizer, metrics);
  }
}
