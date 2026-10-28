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
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingSchemaUrls;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsLambdaSqsInstrumenterFactory {
  private static final String PROCESS_OPERATION_NAME = "process";

  public static Instrumenter<SQSEvent, Void> forEvent(
      OpenTelemetry openTelemetry, String instrumentationName) {
    SqsEventAttributesGetter getter = new SqsEventAttributesGetter();
    InstrumenterBuilder<SQSEvent, Void> builder =
        Instrumenter.<SQSEvent, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME))
            .setSchemaUrl(MessagingSchemaUrls.SCHEMA_URL)
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

  public static Instrumenter<SQSMessage, Boolean> forSelectedMessageProcessing(
      OpenTelemetry openTelemetry, String instrumentationName) {
    SqsMessageAttributesGetter getter = new SqsMessageAttributesGetter();
    InstrumenterBuilder<SQSMessage, Boolean> builder =
        Instrumenter.<SQSMessage, Boolean>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME))
            .setSchemaUrl(MessagingSchemaUrls.SCHEMA_URL)
            .addAttributesExtractor(
                MessagingAttributesExtractor.create(
                    getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME))
            .addAttributesExtractor(new MessageProcessingAttributesExtractor())
            .addOperationMetrics(MessagingProcessMetrics.get());
    builder.setSpanStatusExtractor(
        (spanStatusBuilder, request, response, error) -> {
          if (error == null && Boolean.FALSE.equals(response)) {
            spanStatusBuilder.setStatus(StatusCode.ERROR);
          } else {
            SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, response, error);
          }
        });
    setMessagingProcessExceptionEventExtractor(builder);
    return MessagingProcessInstrumenterFactory.create(
        builder, AwsXrayPropagator.getInstance(), SqsMessageTextMapGetter.INSTANCE);
  }

  private AwsLambdaSqsInstrumenterFactory() {}

  private static final class MessageProcessingAttributesExtractor
      implements AttributesExtractor<SQSMessage, Boolean> {

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, SQSMessage message) {}

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        SQSMessage message,
        @Nullable Boolean response,
        @Nullable Throwable error) {
      if (error == null && Boolean.FALSE.equals(response)) {
        attributes.put(ERROR_TYPE, "_OTHER");
      }
    }
  }
}
