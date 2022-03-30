/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;

/** Entrypoint for instrumenting Lettuce or clients. */
public final class LettuceTelemetry {

  public static final String INSTRUMENTATION_NAME = "io.opentelemetry.lettuce-5.1";

  /** Returns a new {@link LettuceTelemetry} configured with the given {@link OpenTelemetry}. */
  public static LettuceTelemetry create(OpenTelemetry openTelemetry) {
    return new LettuceTelemetry(openTelemetry);
  }

  private final Tracer tracer;

  private LettuceTelemetry(OpenTelemetry openTelemetry) {
    tracer =
        openTelemetry.getTracer(
            INSTRUMENTATION_NAME,
            EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME));
  }

  /**
   * Returns a new {@link Tracing} which can be used with methods like {@link
   * io.lettuce.core.resource.ClientResources.Builder#tracing(Tracing)}.
   */
  public Tracing newTracing() {
    return new OpenTelemetryTracing(tracer);
  }
}
