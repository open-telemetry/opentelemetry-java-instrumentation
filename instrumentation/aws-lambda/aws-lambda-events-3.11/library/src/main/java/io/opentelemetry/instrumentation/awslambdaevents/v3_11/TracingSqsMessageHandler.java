/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v3_11;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal.AwsLambdaSqsInstrumenterFactory;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
    this(
        openTelemetrySdk,
        flushTimeout,
        AwsLambdaSqsInstrumenterFactory.forEvent(openTelemetrySdk, INSTRUMENTATION_NAME));
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
        AwsLambdaSqsInstrumenterFactory.forMessage(openTelemetrySdk, INSTRUMENTATION_NAME));
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
  protected final SQSBatchResponse handleEvent(SQSEvent event, Context context) {
    List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();
    io.opentelemetry.context.Context parentContext = io.opentelemetry.context.Context.current();
    for (SQSMessage message : event.getRecords()) {
      if (messageInstrumenter.shouldStart(parentContext, message)) {
        io.opentelemetry.context.Context otelContext =
            messageInstrumenter.start(parentContext, message);
        Throwable error = null;
        try (Scope ignored = otelContext.makeCurrent()) {
          handleMessage(message, context, batchItemFailures);
        } catch (Throwable t) {
          error = t;
          throw t;
        } finally {
          messageInstrumenter.end(otelContext, message, null, error);
        }
      } else {
        handleMessage(message, context, batchItemFailures);
      }
    }

    return new SQSBatchResponse(batchItemFailures);
  }

  private void handleMessage(
      SQSMessage message,
      Context context,
      List<SQSBatchResponse.BatchItemFailure> batchItemFailures) {
    if (!handleMessage(message, context)) {
      batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
    }
  }

  /**
   * Handles a {@linkplain SQSMessage message}. Implement this class to do the actual processing of
   * incoming SQS messages.
   *
   * @return {@code true} when message was processed successfully, {@code false} when it should be
   *     reported as a failed batch item.
   */
  protected abstract boolean handleMessage(SQSMessage message, Context context);
}
