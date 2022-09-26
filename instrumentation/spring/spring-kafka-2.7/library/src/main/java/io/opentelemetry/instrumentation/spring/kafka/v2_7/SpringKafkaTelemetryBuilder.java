/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory;
import java.util.List;

/** A builder of {@link SpringKafkaTelemetry}. */
public final class SpringKafkaTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-kafka-2.7";

  private final OpenTelemetry openTelemetry;
  private List<String> capturedHeaders = emptyList();
  private boolean captureExperimentalSpanAttributes = false;
  private boolean propagationEnabled = true;
  private boolean messagingReceiveInstrumentationEnabled = false;

  SpringKafkaTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @CanIgnoreReturnValue
  public SpringKafkaTelemetryBuilder setCapturedHeaders(List<String> capturedHeaders) {
    this.capturedHeaders = capturedHeaders;
    return this;
  }

  @CanIgnoreReturnValue
  public SpringKafkaTelemetryBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  @CanIgnoreReturnValue
  public SpringKafkaTelemetryBuilder setPropagationEnabled(boolean propagationEnabled) {
    this.propagationEnabled = propagationEnabled;
    return this;
  }

  @CanIgnoreReturnValue
  public SpringKafkaTelemetryBuilder setMessagingReceiveInstrumentationEnabled(
      boolean messagingReceiveInstrumentationEnabled) {
    this.messagingReceiveInstrumentationEnabled = messagingReceiveInstrumentationEnabled;
    return this;
  }

  /**
   * Returns a new {@link SpringKafkaTelemetry} with the settings of this {@link
   * SpringKafkaTelemetryBuilder}.
   */
  public SpringKafkaTelemetry build() {
    KafkaInstrumenterFactory factory =
        new KafkaInstrumenterFactory(openTelemetry, INSTRUMENTATION_NAME)
            .setCapturedHeaders(capturedHeaders)
            .setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes)
            .setPropagationEnabled(propagationEnabled)
            .setMessagingReceiveInstrumentationEnabled(messagingReceiveInstrumentationEnabled)
            .setErrorCauseExtractor(SpringKafkaErrorCauseExtractor.INSTANCE);

    return new SpringKafkaTelemetry(
        factory.createConsumerProcessInstrumenter(), factory.createBatchProcessInstrumenter());
  }
}
