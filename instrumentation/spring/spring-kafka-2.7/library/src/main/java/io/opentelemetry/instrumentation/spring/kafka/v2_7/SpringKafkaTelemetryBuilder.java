/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.internal.SpringKafkaErrorCauseExtractor;
import java.util.Collection;

/** A builder of {@link SpringKafkaTelemetry}. */
public final class SpringKafkaTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-kafka-2.7";

  private final OpenTelemetry openTelemetry;
  private IncludeExclude headers = IncludeExclude.builder().build();
  private boolean captureExperimentalSpanAttributes = false;
  private boolean messagingReceiveInstrumentationEnabled = false;

  SpringKafkaTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Configures which message headers are captured as span attributes.
   *
   * <p>Header values are captured under the {@code messaging.header.<name>} attribute key. The
   * {@code <name>} part in the attribute key is the header name with dashes replaced by underscores
   * unless {@code otel.instrumentation.common.v3-preview} is enabled, in which case dashes are
   * preserved.
   *
   * <p>Matching is case-sensitive. {@code ?} matches one character and {@code *} matches any number
   * of characters, including none. Excluded patterns take precedence over included patterns. A
   * selector with no included patterns captures every header that is not excluded, and an
   * {@linkplain IncludeExclude#isEmpty() empty} selector captures no headers.
   */
  @CanIgnoreReturnValue
  public SpringKafkaTelemetryBuilder setHeaders(IncludeExclude headers) {
    this.headers = headers;
    return this;
  }

  /**
   * Configures the messaging headers that will be captured as span attributes.
   *
   * @param capturedHeaders A list of messaging header names.
   * @deprecated Use {@link #setHeaders(IncludeExclude)} instead. May be removed in the next minor
   *     release.
   */
  @Deprecated // may be removed in the next minor release
  @CanIgnoreReturnValue
  public SpringKafkaTelemetryBuilder setCapturedHeaders(Collection<String> capturedHeaders) {
    return setHeaders(IncludeExclude.builder().setIncluded(capturedHeaders).build());
  }

  @CanIgnoreReturnValue
  public SpringKafkaTelemetryBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  /**
   * Set whether to capture the consumer message receive telemetry in messaging instrumentation.
   *
   * <p>Note that this will cause the consumer side to start a new trace, with only a span link
   * connecting it to the producer trace.
   */
  @CanIgnoreReturnValue
  public SpringKafkaTelemetryBuilder setMessagingReceiveTelemetryEnabled(
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
            .setHeaders(headers)
            .setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes)
            .setMessagingReceiveTelemetryEnabled(messagingReceiveInstrumentationEnabled)
            .setErrorCauseExtractor(new SpringKafkaErrorCauseExtractor());

    return new SpringKafkaTelemetry(
        factory.createConsumerProcessInstrumenter(), factory.createBatchProcessInstrumenter());
  }
}
