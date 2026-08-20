/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProducerRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.kafka.clients.producer.RecordMetadata;

public final class KafkaTelemetryBuilder {
  static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-2.6";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<KafkaProducerRequest, RecordMetadata>>
      producerAttributesExtractors = new ArrayList<>();
  private final List<AttributesExtractor<KafkaProcessRequest, Void>>
      consumerProcessAttributesExtractors = new ArrayList<>();
  private final List<AttributesExtractor<KafkaReceiveRequest, Void>>
      consumerReceiveAttributesExtractors = new ArrayList<>();
  private IncludeExclude headers = IncludeExclude.builder().build();
  private boolean captureExperimentalSpanAttributes = false;
  private boolean propagationEnabled = true;
  private boolean messagingReceiveInstrumentationEnabled = false;
  private boolean messagingReceiveInstrumentationConfigured = false;

  KafkaTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = requireNonNull(openTelemetry);
  }

  @CanIgnoreReturnValue
  public KafkaTelemetryBuilder addProducerAttributesExtractors(
      AttributesExtractor<KafkaProducerRequest, RecordMetadata> extractor) {
    producerAttributesExtractors.add(extractor);
    return this;
  }

  @CanIgnoreReturnValue
  public KafkaTelemetryBuilder addConsumerProcessAttributesExtractors(
      AttributesExtractor<KafkaProcessRequest, Void> extractor) {
    consumerProcessAttributesExtractors.add(extractor);
    return this;
  }

  @CanIgnoreReturnValue
  public KafkaTelemetryBuilder addConsumerReceiveAttributesExtractors(
      AttributesExtractor<KafkaReceiveRequest, Void> extractor) {
    consumerReceiveAttributesExtractors.add(extractor);
    return this;
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
  public KafkaTelemetryBuilder setHeaders(IncludeExclude headers) {
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
  public KafkaTelemetryBuilder setCapturedHeaders(Collection<String> capturedHeaders) {
    return setHeaders(IncludeExclude.builder().setIncluded(capturedHeaders).build());
  }

  /**
   * Sets whether experimental attributes should be set to spans. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions.
   */
  @CanIgnoreReturnValue
  public KafkaTelemetryBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  /**
   * Set whether to propagate trace context in producers. Enabled by default.
   *
   * <p>You will need to disable this if there are kafka consumers using kafka-clients version prior
   * to 0.11, since those old versions do not support headers, and attaching trace context
   * propagation headers upstream causes those consumers to fail when reading the messages.
   */
  @CanIgnoreReturnValue
  public KafkaTelemetryBuilder setPropagationEnabled(boolean propagationEnabled) {
    this.propagationEnabled = propagationEnabled;
    return this;
  }

  /**
   * Set whether to capture the consumer message receive telemetry in messaging instrumentation.
   *
   * <p>Note that this will cause the consumer side to start a new trace, with only a span link
   * connecting it to the producer trace.
   */
  @CanIgnoreReturnValue
  public KafkaTelemetryBuilder setMessagingReceiveTelemetryEnabled(
      boolean messagingReceiveInstrumentationEnabled) {
    this.messagingReceiveInstrumentationEnabled = messagingReceiveInstrumentationEnabled;
    this.messagingReceiveInstrumentationConfigured = true;
    return this;
  }

  public KafkaTelemetry build() {
    KafkaInstrumenterFactory instrumenterFactory =
        new KafkaInstrumenterFactory(openTelemetry, INSTRUMENTATION_NAME)
            .setHeaders(headers)
            .setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes);
    if (messagingReceiveInstrumentationConfigured) {
      instrumenterFactory.setMessagingReceiveTelemetryEnabled(
          messagingReceiveInstrumentationEnabled);
    }

    return new KafkaTelemetry(
        openTelemetry,
        instrumenterFactory.createProducerInstrumenter(producerAttributesExtractors),
        instrumenterFactory.createConsumerReceiveInstrumenter(consumerReceiveAttributesExtractors),
        instrumenterFactory.createConsumerProcessInstrumenter(consumerProcessAttributesExtractors),
        propagationEnabled);
  }
}
