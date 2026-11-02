/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSendExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProducerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingSchemaUrls;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

final class VertxEventBusSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-eventbus-3.0";

  // messaging.operation.name values, named after the Vert.x EventBus API operations
  private static final String SEND_OPERATION_NAME = "send";
  private static final String PUBLISH_OPERATION_NAME = "publish";
  private static final String REQUEST_OPERATION_NAME = "request";
  private static final String PROCESS_OPERATION_NAME = "process";

  private static final Instrumenter<EventBusRequest, Void> sendInstrumenter =
      createProducerInstrumenter(SEND_OPERATION_NAME);
  private static final Instrumenter<EventBusRequest, Void> publishInstrumenter =
      createProducerInstrumenter(PUBLISH_OPERATION_NAME);
  private static final Instrumenter<EventBusRequest, Void> requestInstrumenter =
      createProducerInstrumenter(REQUEST_OPERATION_NAME);
  private static final Instrumenter<EventBusRequest, Void> consumerInstrumenter =
      createConsumerInstrumenter();

  static Instrumenter<EventBusRequest, Void> sendInstrumenter() {
    return sendInstrumenter;
  }

  static Instrumenter<EventBusRequest, Void> publishInstrumenter() {
    return publishInstrumenter;
  }

  static Instrumenter<EventBusRequest, Void> requestInstrumenter() {
    return requestInstrumenter;
  }

  static Instrumenter<EventBusRequest, Void> consumerInstrumenter() {
    return consumerInstrumenter;
  }

  static TextMapPropagator propagator() {
    return GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
  }

  private static Instrumenter<EventBusRequest, Void> createProducerInstrumenter(
      String operationName) {
    EventBusMessageAttributesGetter getter = EventBusMessageAttributesGetter.INSTANCE;
    InstrumenterBuilder<EventBusRequest, Void> builder =
        Instrumenter.<EventBusRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.SEND, operationName))
            .setSchemaUrl(MessagingSchemaUrls.SCHEMA_URL)
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(
                        getter, MessagingOperationType.SEND, operationName)
                    .setHeaders(ExperimentalConfig.get().getMessagingHeaders())
                    .build())
            .addOperationMetrics(MessagingProducerMetrics.get());
    setMessagingSendExceptionEventExtractor(builder);
    // Header injection happens in CreateMessageInstrumentation against the created message, the
    // EventBusRequest has no message to inject into on the producer side.
    return builder.buildInstrumenter(SpanKindExtractor.alwaysProducer());
  }

  private static Instrumenter<EventBusRequest, Void> createConsumerInstrumenter() {
    EventBusMessageAttributesGetter getter = EventBusMessageAttributesGetter.INSTANCE;
    InstrumenterBuilder<EventBusRequest, Void> builder =
        Instrumenter.<EventBusRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME))
            .setSchemaUrl(MessagingSchemaUrls.SCHEMA_URL)
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(
                        getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME)
                    .setHeaders(ExperimentalConfig.get().getMessagingHeaders())
                    .build())
            .addOperationMetrics(MessagingProcessMetrics.get())
            .addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages());
    setMessagingProcessExceptionEventExtractor(builder);
    return MessagingProcessInstrumenterFactory.create(
        builder, propagator(), MessageHeaderGetter.INSTANCE);
  }

  private VertxEventBusSingletons() {}
}
