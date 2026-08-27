/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSendExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSettleExceptionEventExtractor;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProducerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NatsInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.nats-2.17";

  // messaging.operation.name values, named after the NATS API operations
  private static final String PUBLISH_OPERATION_NAME = "publish";
  private static final String REQUEST_OPERATION_NAME = "request";
  private static final String PROCESS_OPERATION_NAME = "process";

  public static Instrumenter<NatsRequest, NatsRequest> createPublishInstrumenter(
      OpenTelemetry openTelemetry, IncludeExclude headers) {
    return createProducerInstrumenter(openTelemetry, headers, PUBLISH_OPERATION_NAME);
  }

  public static Instrumenter<NatsRequest, NatsRequest> createRequestInstrumenter(
      OpenTelemetry openTelemetry, IncludeExclude headers) {
    return createProducerInstrumenter(openTelemetry, headers, REQUEST_OPERATION_NAME);
  }

  private static Instrumenter<NatsRequest, NatsRequest> createProducerInstrumenter(
      OpenTelemetry openTelemetry, IncludeExclude headers, String operationName) {
    NatsRequestMessagingAttributesGetter getter = new NatsRequestMessagingAttributesGetter(false);
    InstrumenterBuilder<NatsRequest, NatsRequest> builder =
        Instrumenter.<NatsRequest, NatsRequest>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.SEND, operationName))
            .addAttributesExtractor(
                messagingAttributesExtractor(
                    getter, MessagingOperationType.SEND, operationName, headers))
            .addOperationMetrics(MessagingProducerMetrics.getForOperationType());
    setMessagingSendExceptionEventExtractor(builder);
    return builder.buildProducerInstrumenter(new NatsRequestTextMapSetter());
  }

  public static Instrumenter<NatsRequest, NatsRequest> createSettleInstrumenter(
      OpenTelemetry openTelemetry, IncludeExclude headers) {
    NatsRequestMessagingAttributesGetter getter = new NatsRequestMessagingAttributesGetter(true);
    InstrumenterBuilder<NatsRequest, NatsRequest> builder =
        Instrumenter.<NatsRequest, NatsRequest>builder(
                openTelemetry, INSTRUMENTATION_NAME, NatsSpanNameExtractor.create(getter, "settle"))
            .addAttributesExtractor(
                messagingAttributesExtractor(
                    getter, MessagingOperationType.SETTLE, "settle", headers))
            .addAttributesExtractor(new NatsSettlementOperationNameExtractor())
            .addOperationMetrics(MessagingConsumerMetrics.getForOperationType());
    setMessagingSettleExceptionEventExtractor(builder);
    return builder.buildClientInstrumenter(new NatsRequestTextMapSetter());
  }

  public static Instrumenter<NatsRequest, Void> createConsumerProcessInstrumenter(
      OpenTelemetry openTelemetry, IncludeExclude headers) {
    NatsRequestMessagingAttributesGetter getter = new NatsRequestMessagingAttributesGetter(false);
    InstrumenterBuilder<NatsRequest, Void> builder =
        Instrumenter.<NatsRequest, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME))
            .addAttributesExtractor(
                messagingAttributesExtractor(
                    getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME, headers))
            .addOperationMetrics(MessagingProcessMetrics.get())
            .addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages());
    setMessagingProcessExceptionEventExtractor(builder);

    return MessagingProcessInstrumenterFactory.create(
        builder,
        openTelemetry.getPropagators().getTextMapPropagator(),
        new NatsRequestTextMapGetter(),
        false);
  }

  private static AttributesExtractor<NatsRequest, Object> messagingAttributesExtractor(
      NatsRequestMessagingAttributesGetter getter,
      MessagingOperationType operationType,
      String operationName,
      IncludeExclude headers) {
    return MessagingAttributesExtractor.builder(getter, operationType, operationName)
        .setHeaders(headers)
        .build();
  }

  private NatsInstrumenterFactory() {}
}
