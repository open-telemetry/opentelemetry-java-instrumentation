/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.time.Duration;

public abstract class TracingSqsMessageHandler extends TracingSqsEventHandler {

  private final Instrumenter<SQSMessage, Void> messageInstrumenter;

  /**
   * Creates a new {@link TracingSqsMessageHandler} which traces using the provided {@link
   * OpenTelemetrySdk} and has a timeout of 1s when flushing at the end of an invocation.
   */
  protected TracingSqsMessageHandler(OpenTelemetrySdk openTelemetrySdk) {
    this(openTelemetrySdk, DEFAULT_FLUSH_TIMEOUT);
  }

  /**
   * Creates a new {@link TracingSqsMessageHandler} which traces using the provided {@link
   * OpenTelemetrySdk} and has a timeout of {@code flushTimeout} when flushing at the end of an
   * invocation.
   */
  protected TracingSqsMessageHandler(OpenTelemetrySdk openTelemetrySdk, Duration flushTimeout) {
    this(openTelemetrySdk, flushTimeout, AwsLambdaSqsInstrumenterFactory.forEvent(openTelemetrySdk));
  }

  /**
   * Creates a new {@link TracingSqsMessageHandler} which flushes the provided {@link
   * OpenTelemetrySdk}, has a timeout of {@code flushTimeout} when flushing at the end of an
   * invocation, and instruments {@link SQSEvent} using the provided {@code Instrumenter<SQSEvent,
   * Void>}.
   */
  protected TracingSqsMessageHandler(
      OpenTelemetrySdk openTelemetrySdk,
      Duration flushTimeout,
      Instrumenter<SQSEvent, Void> eventInstrumenter) {
    this(
        openTelemetrySdk,
        flushTimeout,
        eventInstrumenter,
        AwsLambdaSqsInstrumenterFactory.forMessage(openTelemetrySdk));
  }

  /**
   * Creates a new {@link TracingSqsMessageHandler} which flushes the provided {@link
   * OpenTelemetrySdk}, has a timeout of {@code flushTimeout} when flushing at the end of an
   * invocation, and traces using the provided {@code Instrumenter<SQSEvent, Void>} and {@code
   * Instrumenter<SQSMessage, Void>}.
   */
  protected TracingSqsMessageHandler(
      OpenTelemetrySdk openTelemetrySdk,
      Duration flushTimeout,
      Instrumenter<SQSEvent, Void> eventInstrumenter,
      Instrumenter<SQSMessage, Void> messageInstrumenter) {
    super(openTelemetrySdk, flushTimeout, eventInstrumenter);
    this.messageInstrumenter = messageInstrumenter;
  }

  @Override
  protected final void handleEvent(SQSEvent event, Context context) {
    io.opentelemetry.context.Context parentContext = io.opentelemetry.context.Context.current();
    for (SQSMessage message : event.getRecords()) {
      if (messageInstrumenter.shouldStart(parentContext, message)) {
        io.opentelemetry.context.Context otelContext =
            messageInstrumenter.start(parentContext, message);
        Throwable error = null;
        try (Scope ignored = otelContext.makeCurrent()) {
          handleMessage(message, context);
        } catch (Throwable t) {
          error = t;
          throw t;
        } finally {
          messageInstrumenter.end(otelContext, message, null, error);
        }
      } else {
        handleMessage(message, context);
      }
    }
  }

  /**
   * Handles a {@linkplain SQSMessage message}. Implement this class to do the actual processing of
   * incoming SQS messages.
   */
  protected abstract void handleMessage(SQSMessage message, Context context);
}
