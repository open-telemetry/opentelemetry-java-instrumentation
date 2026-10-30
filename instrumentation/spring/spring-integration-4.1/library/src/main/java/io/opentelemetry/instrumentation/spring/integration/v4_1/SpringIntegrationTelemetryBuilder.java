/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSendExceptionEventExtractor;
import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProducerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingSchemaUrls;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** A builder of {@link SpringIntegrationTelemetry}. */
public final class SpringIntegrationTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-integration-4.1";

  // messaging.operation.name values, named after the Spring Messaging API operations
  private static final String SEND_OPERATION_NAME = "send";
  private static final String PROCESS_OPERATION_NAME = "process";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<MessageWithChannel, Void>> additionalAttributeExtractors =
      new ArrayList<>();

  private Collection<String> headers = emptyList();
  private boolean producerSpanEnabled = false;

  SpringIntegrationTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public SpringIntegrationTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<MessageWithChannel, Void> attributesExtractor) {
    additionalAttributeExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures which message headers are captured as span attributes.
   *
   * <p>Header values are captured under the {@code messaging.header.<name>} attribute key. The
   * {@code <name>} part in the attribute key is the header name with its original spelling,
   * including dashes.
   *
   * <p>Names are looked up literally using the messaging library's header-name case sensitivity.
   * {@code *} and {@code ?} are not wildcards. No headers are captured by default.
   */
  @CanIgnoreReturnValue
  public SpringIntegrationTelemetryBuilder setCapturedHeaders(Collection<String> capturedHeaders) {
    this.headers = new ArrayList<>(capturedHeaders);
    return this;
  }

  /**
   * Sets whether additional {@link SpanKind#PRODUCER PRODUCER} span should be emitted by this
   * instrumentation.
   */
  @CanIgnoreReturnValue
  public SpringIntegrationTelemetryBuilder setProducerSpanEnabled(boolean producerSpanEnabled) {
    this.producerSpanEnabled = producerSpanEnabled;
    return this;
  }

  /**
   * Returns a new {@link SpringIntegrationTelemetry} with the settings of this {@link
   * SpringIntegrationTelemetryBuilder}.
   */
  public SpringIntegrationTelemetry build() {
    SpringMessagingAttributesGetter consumerGetter = new SpringMessagingAttributesGetter();
    InstrumenterBuilder<MessageWithChannel, Void> consumerBuilder =
        Instrumenter.<MessageWithChannel, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    consumerGetter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME))
            .setSchemaUrl(MessagingSchemaUrls.SCHEMA_URL)
            .addAttributesExtractors(additionalAttributeExtractors)
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    consumerGetter,
                    MessagingOperationType.PROCESS,
                    PROCESS_OPERATION_NAME,
                    headers))
            .addOperationMetrics(SpringIntegrationConsumerMetrics::new);
    setMessagingProcessExceptionEventExtractor(consumerBuilder);
    Instrumenter<MessageWithChannel, Void> consumerInstrumenter =
        MessagingProcessInstrumenterFactory.create(
            consumerBuilder,
            openTelemetry.getPropagators().getTextMapPropagator(),
            MessageHeadersGetter.INSTANCE);

    SpringMessagingAttributesGetter producerGetter = new SpringMessagingAttributesGetter();
    InstrumenterBuilder<MessageWithChannel, Void> producerBuilder =
        Instrumenter.<MessageWithChannel, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    producerGetter, MessagingOperationType.SEND, SEND_OPERATION_NAME))
            .setSchemaUrl(MessagingSchemaUrls.SCHEMA_URL)
            .addAttributesExtractors(additionalAttributeExtractors)
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    producerGetter, MessagingOperationType.SEND, SEND_OPERATION_NAME, headers))
            .addOperationMetrics(MessagingProducerMetrics.get());
    setMessagingSendExceptionEventExtractor(producerBuilder);
    Instrumenter<MessageWithChannel, Void> producerInstrumenter =
        producerBuilder.buildInstrumenter(SpanKindExtractor.alwaysProducer());
    return new SpringIntegrationTelemetry(
        openTelemetry.getPropagators(),
        consumerInstrumenter,
        producerInstrumenter,
        producerSpanEnabled);
  }

  private static AttributesExtractor<MessageWithChannel, Void> buildMessagingAttributesExtractor(
      MessagingAttributesGetter<MessageWithChannel, Void> getter,
      MessagingOperationType operationType,
      String operationName,
      Collection<String> headers) {
    return MessagingAttributesExtractor.builder(getter, operationType, operationName)
        .setCapturedHeaders(headers)
        .build();
  }
}
