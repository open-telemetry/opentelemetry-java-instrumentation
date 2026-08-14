/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

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
                emitStableMessagingSemconv()
                    ? MessagingSpanNameExtractor.create(
                        getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME)
                    : event -> SqsEventAttributesGetter.source(event) + " process")
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

  public static Instrumenter<SQSMessage, Void> forMessage(
      OpenTelemetry openTelemetry, String instrumentationName) {
    SqsMessageAttributesGetter getter = new SqsMessageAttributesGetter();
    InstrumenterBuilder<SQSMessage, Void> builder =
        Instrumenter.<SQSMessage, Void>builder(
                openTelemetry,
                instrumentationName,
                emitStableMessagingSemconv()
                    ? MessagingSpanNameExtractor.create(
                        getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME)
                    : message -> message.getEventSource() + " process")
            .addAttributesExtractor(
                emitStableMessagingSemconv()
                    ? MessagingAttributesExtractor.create(
                        getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME)
                    : new SpanKeyOmittingAttributesExtractor<>(
                        MessagingAttributesExtractor.create(
                            getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME)))
            .addOperationMetrics(MessagingProcessMetrics.get());
    setMessagingProcessExceptionEventExtractor(builder);
    return MessagingProcessInstrumenterFactory.create(
        builder, AwsXrayPropagator.getInstance(), SqsMessageTextMapGetter.INSTANCE, true);
  }

  private AwsLambdaSqsInstrumenterFactory() {}
}
