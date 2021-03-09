/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

/** Entrypoint for tracing Lettuce or clients. */
public final class LettuceTracing {

  /** Returns a new {@link LettuceTracing} configured with the given {@link OpenTelemetry}. */
  public static LettuceTracing create(OpenTelemetry openTelemetry) {
    return new LettuceTracing(openTelemetry);
  }

  private final Tracer tracer;

  private LettuceTracing(OpenTelemetry openTelemetry) {
    tracer = openTelemetry.getTracer("io.opentelemetry.javaagent.lettuce-5.1");
  }

  /**
   * Returns a new {@link Tracing} which can be used with methods like {@link
   * io.lettuce.core.resource.ClientResources.Builder#tracing(Tracing)}.
   */
  public Tracing newTracing() {
    return new OpenTelemetryTracing(tracer);
  }
}
