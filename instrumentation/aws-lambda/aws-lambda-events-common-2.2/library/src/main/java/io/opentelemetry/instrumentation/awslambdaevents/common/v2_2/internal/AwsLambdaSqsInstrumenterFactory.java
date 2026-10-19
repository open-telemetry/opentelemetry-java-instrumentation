/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsLambdaSqsInstrumenterFactory {
  private static final String PROCESS_OPERATION_NAME = "process";
  private static final ContextKey<Boolean> MESSAGE_PROCESSING_SUCCESSFUL =
      ContextKey.named("aws-lambda-sqs-message-processing-successful");

  public static Instrumenter<SQSEvent, Void> forEvent(
      OpenTelemetry openTelemetry, String instrumentationName) {
    SqsEventAttributesGetter getter = new SqsEventAttributesGetter();
    InstrumenterBuilder<SQSEvent, Void> builder =
        Instrumenter.<SQSEvent, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME))
            .addAttributesExtractor(
                new SpanKeyOmittingAttributesExtractor<>(
                    MessagingAttributesExtractor.create(
                        getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME)))
            .addSpanLinksExtractor(new SqsEventSpanLinksExtractor())
            .addOperationMetrics(MessagingProcessMetrics.get())
            .addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages());
    setMessagingProcessExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public static Instrumenter<SQSMessage, Void> forSelectedMessageProcessing(
      OpenTelemetry openTelemetry, String instrumentationName) {
    SqsMessageAttributesGetter getter = new SqsMessageAttributesGetter();
    InstrumenterBuilder<SQSMessage, Void> builder =
        Instrumenter.<SQSMessage, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME))
            .addAttributesExtractor(
                MessagingAttributesExtractor.create(
                    getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME))
            .addAttributesExtractor(new MessageProcessingOutcomeExtractor())
            .addOperationMetrics(MessagingProcessMetrics.get());
    setMessagingProcessExceptionEventExtractor(builder);
    return MessagingProcessInstrumenterFactory.create(
        builder, AwsXrayPropagator.getInstance(), SqsMessageTextMapGetter.INSTANCE);
  }

  public static Context withMessageProcessingOutcome(Context context, boolean successful) {
    if (successful) {
      return context;
    }
    // The protected handler API uses a Void response, so carry the outcome in the end context.
    Span.fromContext(context).setStatus(StatusCode.ERROR);
    return context.with(MESSAGE_PROCESSING_SUCCESSFUL, false);
  }

  private AwsLambdaSqsInstrumenterFactory() {}

  private static final class MessageProcessingOutcomeExtractor
      implements AttributesExtractor<SQSMessage, Void> {

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, SQSMessage message) {}

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        SQSMessage message,
        @Nullable Void response,
        @Nullable Throwable error) {
      if (error == null && Boolean.FALSE.equals(context.get(MESSAGE_PROCESSING_SUCCESSFUL))) {
        attributes.put(ERROR_TYPE, "_OTHER");
      }
    }
  }
}
