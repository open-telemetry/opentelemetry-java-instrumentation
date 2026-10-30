/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingReceiveExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSendExceptionEventExtractor;
import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProducerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanKindExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingSchemaUrls;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import java.util.Collection;

public class JmsInstrumenterFactory {

  // messaging.operation.name values, named after the JMS API operations
  private static final String SEND_OPERATION_NAME = "send";
  private static final String RECEIVE_OPERATION_NAME = "receive";
  private static final String PROCESS_OPERATION_NAME = "process";

  private final OpenTelemetry openTelemetry;
  private final String instrumentationName;
  private Collection<String> headers = emptyList();

  public JmsInstrumenterFactory(OpenTelemetry openTelemetry, String instrumentationName) {
    this.openTelemetry = openTelemetry;
    this.instrumentationName = instrumentationName;
  }

  @CanIgnoreReturnValue
  public JmsInstrumenterFactory setCapturedHeaders(Collection<String> headers) {
    this.headers = headers;
    return this;
  }

  public Instrumenter<MessageWithDestination, Void> createProducerInstrumenter() {
    JmsMessageAttributesGetter getter = new JmsMessageAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.SEND;

    InstrumenterBuilder<MessageWithDestination, Void> builder =
        Instrumenter.<MessageWithDestination, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operationType, SEND_OPERATION_NAME))
            .setSchemaUrl(MessagingSchemaUrls.SCHEMA_URL)
            .addAttributesExtractor(
                createMessagingAttributesExtractor(operationType, SEND_OPERATION_NAME))
            .addOperationMetrics(MessagingProducerMetrics.get());
    setMessagingSendExceptionEventExtractor(builder);
    return builder.buildProducerInstrumenter(new MessagePropertySetter());
  }

  public Instrumenter<MessageWithDestination, Void> createConsumerReceiveInstrumenter() {
    JmsMessageAttributesGetter getter = new JmsMessageAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.RECEIVE;

    InstrumenterBuilder<MessageWithDestination, Void> builder =
        Instrumenter.<MessageWithDestination, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operationType, RECEIVE_OPERATION_NAME))
            .setSchemaUrl(MessagingSchemaUrls.SCHEMA_URL)
            .addAttributesExtractor(
                createMessagingAttributesExtractor(operationType, RECEIVE_OPERATION_NAME))
            .addOperationMetrics(MessagingConsumerMetrics.get());
    setMessagingReceiveExceptionEventExtractor(builder);
    builder.addSpanLinksExtractor(
        new PropagatorBasedSpanLinksExtractor<>(
            openTelemetry.getPropagators().getTextMapPropagator(), MessagePropertyGetter.INSTANCE));
    return builder.buildInstrumenter(MessagingSpanKindExtractor.create(operationType));
  }

  public Instrumenter<MessageWithDestination, Void> createConsumerProcessInstrumenter(
      boolean recordConsumedMessages) {
    JmsMessageAttributesGetter getter = new JmsMessageAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;

    InstrumenterBuilder<MessageWithDestination, Void> builder =
        Instrumenter.<MessageWithDestination, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operationType, PROCESS_OPERATION_NAME))
            .setSchemaUrl(MessagingSchemaUrls.SCHEMA_URL)
            .addAttributesExtractor(
                createMessagingAttributesExtractor(operationType, PROCESS_OPERATION_NAME));
    if (recordConsumedMessages) {
      builder.addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages());
    }
    builder.addOperationMetrics(JmsProcessMetrics.create());
    setMessagingProcessExceptionEventExtractor(builder);
    return MessagingProcessInstrumenterFactory.create(
        builder,
        openTelemetry.getPropagators().getTextMapPropagator(),
        MessagePropertyGetter.INSTANCE);
  }

  private AttributesExtractor<MessageWithDestination, Void> createMessagingAttributesExtractor(
      MessagingOperationType operationType, String operationName) {
    return MessagingAttributesExtractor.builder(
            new JmsMessageAttributesGetter(), operationType, operationName)
        .setCapturedHeaders(headers)
        .build();
  }
}
