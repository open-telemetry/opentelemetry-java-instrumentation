/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

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

  private final Instrumenter<LettuceRequest, LettuceResponse> instrumenter;
  private final RedisCommandSanitizer sanitizer;
  private final boolean encodingEventsEnabled;

  LettuceTelemetry(
      Instrumenter<LettuceRequest, LettuceResponse> instrumenter,
      boolean querySanitizationEnabled,
      boolean encodingEventsEnabled) {
    this.instrumenter = instrumenter;
    this.sanitizer = RedisCommandSanitizer.create(querySanitizationEnabled);
    this.encodingEventsEnabled = encodingEventsEnabled;
  }

  /**
   * Returns a new {@link Tracing} which can be used with methods like {@link
   * io.lettuce.core.resource.ClientResources.Builder#tracing(Tracing)}.
   */
  public Tracing createTracing() {
    return new OpenTelemetryTracing(instrumenter, sanitizer, encodingEventsEnabled);
  }

  /**
   * Returns a new {@link Tracing} which can be used with methods like {@link
   * io.lettuce.core.resource.ClientResources.Builder#tracing(Tracing)}.
   *
   * @deprecated Use {@link #createTracing()} instead.
   */
  @Deprecated
  public Tracing newTracing() {
    return createTracing();
  }
}
