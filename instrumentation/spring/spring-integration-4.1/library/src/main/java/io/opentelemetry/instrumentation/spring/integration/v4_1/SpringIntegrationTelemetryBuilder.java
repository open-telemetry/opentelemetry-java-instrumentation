/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration.v4_1;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingExceptionEventExtractors;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.Experimental;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** A builder of {@link SpringIntegrationTelemetry}. */
public final class SpringIntegrationTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-integration-4.1";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<MessageWithChannel, Void>> additionalAttributeExtractors =
      new ArrayList<>();

  private List<String> capturedHeaders = emptyList();
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
   * Configures the messaging headers that will be captured as span attributes.
   *
   * @param capturedHeaders A list of messaging header names.
   */
  @CanIgnoreReturnValue
  public SpringIntegrationTelemetryBuilder setCapturedHeaders(Collection<String> capturedHeaders) {
    this.capturedHeaders = new ArrayList<>(capturedHeaders);
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

  private static String consumerSpanName(MessageWithChannel messageWithChannel) {
    return messageWithChannel.getChannelName() + " process";
  }

  private static String producerSpanName(MessageWithChannel messageWithChannel) {
    return messageWithChannel.getChannelName() + " publish";
  }

  /**
   * Returns a new {@link SpringIntegrationTelemetry} with the settings of this {@link
   * SpringIntegrationTelemetryBuilder}.
   */
  public SpringIntegrationTelemetry build() {
    InstrumenterBuilder<MessageWithChannel, Void> consumerBuilder =
        Instrumenter.<MessageWithChannel, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                SpringIntegrationTelemetryBuilder::consumerSpanName)
            .addAttributesExtractors(additionalAttributeExtractors)
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    SpringMessagingAttributesGetter.INSTANCE,
                    MessageOperation.PROCESS,
                    capturedHeaders));
    Experimental.setExceptionEventExtractor(
        consumerBuilder, MessagingExceptionEventExtractors.process());
    Instrumenter<MessageWithChannel, Void> consumerInstrumenter =
        consumerBuilder.buildConsumerInstrumenter(MessageHeadersGetter.INSTANCE);

    InstrumenterBuilder<MessageWithChannel, Void> producerBuilder =
        Instrumenter.<MessageWithChannel, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                SpringIntegrationTelemetryBuilder::producerSpanName)
            .addAttributesExtractors(additionalAttributeExtractors)
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    SpringMessagingAttributesGetter.INSTANCE,
                    MessageOperation.PUBLISH,
                    capturedHeaders));
    Experimental.setExceptionEventExtractor(
        producerBuilder, MessagingExceptionEventExtractors.client());
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
      MessageOperation operation,
      List<String> capturedHeaders) {
    return MessagingAttributesExtractor.builder(getter, operation)
        .setCapturedHeaders(capturedHeaders)
        .build();
  }
}
