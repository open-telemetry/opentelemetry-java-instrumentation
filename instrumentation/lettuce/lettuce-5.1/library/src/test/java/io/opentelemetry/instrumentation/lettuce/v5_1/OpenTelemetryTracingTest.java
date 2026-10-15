/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.RedisCommand;
import io.lettuce.core.tracing.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class OpenTelemetryTracingTest {

  @SuppressWarnings("unchecked")
  private final Instrumenter<LettuceRequest, LettuceResponse> instrumenter =
      mock(Instrumenter.class);

  private final Tracer.Span span =
      new LettuceTelemetry(instrumenter, true)
          .createTracing()
          .getTracerProvider()
          .getTracer()
          .nextSpan();

  @BeforeEach
  void setUp() {
    when(instrumenter.shouldStart(any(), any())).thenReturn(true);
    when(instrumenter.start(any(), any())).thenReturn(Context.root());
  }

  @Test
  void finishClaimsCompletionBeforeReentrantEnd() {
    span.start();
    span.tag("error", "ERR command failed");
    IllegalStateException error = new IllegalStateException("command failed");
    span.error(error);
    doAnswer(
            invocation -> {
              assertThat(Thread.holdsLock(span)).isFalse();
              span.finish();
              return null;
            })
        .when(instrumenter)
        .end(any(), any(), any(), any());

    span.finish();
    span.finish();

    ArgumentCaptor<LettuceResponse> response = ArgumentCaptor.forClass(LettuceResponse.class);
    verify(instrumenter).end(eq(Context.root()), any(), response.capture(), eq(error));
    assertThat(response.getValue().getErrorMessage()).isEqualTo("ERR command failed");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void commandCompletionPreservesErrorAttribution(boolean explicitError)
      throws ReflectiveOperationException {
    StatusOutput<String, String> output = new StatusOutput<>(StringCodec.UTF8);
    AsyncCommand<String, String, String> command =
        new AsyncCommand<>(new Command<>(CommandType.SET, output));
    IllegalStateException callbackError = new IllegalStateException("callback error");
    IllegalStateException originalError = new IllegalStateException("original error");
    if (explicitError) {
      span.error(originalError);
    }
    span.tag("error", "earlier error tag");
    start(command);
    List<Boolean> completionLocks = new ArrayList<>();
    doAnswer(
            invocation -> {
              completionLocks.add(Thread.holdsLock(span));
              span.finish();
              return null;
            })
        .when(instrumenter)
        .end(any(), any(), any(), any());

    command.completeExceptionally(callbackError);
    span.finish();

    ArgumentCaptor<LettuceResponse> response = ArgumentCaptor.forClass(LettuceResponse.class);
    verify(instrumenter)
        .end(
            eq(Context.root()),
            any(),
            response.capture(),
            eq(explicitError ? originalError : callbackError));
    assertThat(response.getValue().getErrorMessage()).isEqualTo(callbackError.getMessage());
    assertThat(response.getValue().getThrowable()).isSameAs(callbackError);
    assertThat(completionLocks).containsExactly(false);
  }

  @Test
  void alreadyCompletedCommandEndsOutsideStartupMonitor() throws ReflectiveOperationException {
    AsyncCommand<String, String, String> command =
        new AsyncCommand<>(new Command<>(CommandType.SET, null));
    command.complete();
    List<Boolean> completionLocks = new ArrayList<>();
    doAnswer(
            invocation -> {
              completionLocks.add(Thread.holdsLock(span));
              return null;
            })
        .when(instrumenter)
        .end(any(), any(), any(), isNull());

    start(command);
    span.finish();

    verify(instrumenter).end(eq(Context.root()), any(), any(LettuceResponse.class), isNull());
    assertThat(completionLocks).containsExactly(false);
  }

  @Test
  void directFinishWinsOverLaterCommandCompletion() throws ReflectiveOperationException {
    AsyncCommand<String, String, String> command =
        new AsyncCommand<>(new Command<>(CommandType.SET, new StatusOutput<>(StringCodec.UTF8)));
    start(command);

    span.finish();
    command.completeExceptionally(new IllegalStateException("late error"));

    verify(instrumenter).end(eq(Context.root()), any(), isNull(), isNull());
    verify(instrumenter).end(any(), any(), any(), any());
  }

  @Test
  void endFailurePropagatesWithoutRetry() {
    span.start();
    IllegalStateException error = new IllegalStateException("telemetry failed");
    doThrow(error).when(instrumenter).end(any(), any(), isNull(), isNull());

    assertThatThrownBy(span::finish).isSameAs(error);
    span.finish();

    verify(instrumenter).end(eq(Context.root()), any(), isNull(), isNull());
  }

  private void start(RedisCommand<?, ?, ?> command) throws ReflectiveOperationException {
    // start(RedisCommand) is only part of the Tracer.Span API in Lettuce 6.0+.
    Method start = span.getClass().getMethod("start", RedisCommand.class);
    start.setAccessible(true);
    start.invoke(span, command);
  }
}
