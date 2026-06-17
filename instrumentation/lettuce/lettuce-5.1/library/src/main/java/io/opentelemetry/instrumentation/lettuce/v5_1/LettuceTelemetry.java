/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.lettuce.core.protocol.RedisCommand;
import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;

/** Entrypoint for instrumenting Lettuce or clients. */
public final class LettuceTelemetry {
  public static final String INSTRUMENTATION_NAME = "io.opentelemetry.lettuce-5.1";

  private final Instrumenter<LettuceRequest, LettuceResponse> instrumenter;
  private final RedisCommandSanitizer sanitizer;
  private final boolean encodingEventsEnabled;

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

  /** Used by the javaagent to track Lettuce auto-flush batches. */
  public static void setAutoFlushCommands(Object commands, boolean autoFlush) {
    OpenTelemetryTracing.setAutoFlushCommands(commands, autoFlush);
  }

  /** Used by the javaagent to capture commands while Lettuce auto-flush is disabled. */
  public static void capture(Object commands, RedisCommand<?, ?, ?> command) {
    OpenTelemetryTracing.capture(commands, command);
  }

  /** Used by the javaagent to start an aggregate span for a flushed Lettuce batch. */
  @Nullable
  public static Object startBatch(Object commands) {
    return OpenTelemetryTracing.startBatch(commands);
  }

  /** Used by the javaagent to clear the active flushed batch and end it on synchronous failure. */
  public static void finishBatch(Object batch, @Nullable Throwable throwable) {
    OpenTelemetryTracing.finishBatch(batch, throwable);
  }

  /** Used by the javaagent to activate batch suppression while Lettuce writes a command. */
  public static void startCommand(RedisCommand<?, ?, ?> command) {
    OpenTelemetryTracing.startCommand(command);
  }

  /** Used by the javaagent to clear the active command write batch suppression. */
  public static void endCommand() {
    OpenTelemetryTracing.endCommand();
  }

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
}
