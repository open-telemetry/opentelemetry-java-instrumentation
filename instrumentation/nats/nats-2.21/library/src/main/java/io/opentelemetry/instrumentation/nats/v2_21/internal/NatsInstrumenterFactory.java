/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.", or "This class is internal and experimental. Its APIs are unstable and can change at
 * any time. Its APIs (or a version of them) may be promoted to the public stable API in the future,
 * but no guarantees are made.
 */
public final class NatsInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.nats-2.21";

  public static final SpanNameExtractor<NatsRequest> PRODUCER_SPAN_NAME_EXTRACTOR =
      MessagingSpanNameExtractor.create(
          NatsRequestMessagingAttributesGetter.VOID_INSTANCE, MessageOperation.PUBLISH);

  public static final AttributesExtractor<NatsRequest, Void> PRODUCER_ATTRIBUTES_EXTRACTOR =
      MessagingAttributesExtractor.create(
          NatsRequestMessagingAttributesGetter.VOID_INSTANCE, MessageOperation.PUBLISH);

  public static final SpanNameExtractor<NatsRequest> CONSUMER_SPAN_NAME_EXTRACTOR =
      MessagingSpanNameExtractor.create(
          NatsRequestMessagingAttributesGetter.VOID_INSTANCE, MessageOperation.RECEIVE);

  public static final AttributesExtractor<NatsRequest, Void> CONSUMER_ATTRIBUTES_EXTRACTOR =
      MessagingAttributesExtractor.create(
          NatsRequestMessagingAttributesGetter.VOID_INSTANCE, MessageOperation.RECEIVE);

  public static final AttributesExtractor<NatsRequest, NatsRequest> CLIENT_ATTRIBUTES_EXTRACTOR =
      MessagingAttributesExtractor.create(
          NatsRequestMessagingAttributesGetter.NATS_REQUEST_INSTANCE, MessageOperation.PUBLISH);

  public static Instrumenter<NatsRequest, Void> createProducerInstrumenter(
      OpenTelemetry openTelemetry) {
    return Instrumenter.<NatsRequest, Void>builder(
            openTelemetry, INSTRUMENTATION_NAME, PRODUCER_SPAN_NAME_EXTRACTOR)
        .addAttributesExtractor(PRODUCER_ATTRIBUTES_EXTRACTOR)
        .buildProducerInstrumenter(NatsRequestTextMapSetter.INSTANCE);
  }

  public static Instrumenter<NatsRequest, Void> createConsumerInstrumenter(
      OpenTelemetry openTelemetry) {
    return Instrumenter.<NatsRequest, Void>builder(
            openTelemetry, INSTRUMENTATION_NAME, CONSUMER_SPAN_NAME_EXTRACTOR)
        .addAttributesExtractor(CONSUMER_ATTRIBUTES_EXTRACTOR)
        .addSpanLinksExtractor(
            new PropagatorBasedSpanLinksExtractor<>(
                openTelemetry.getPropagators().getTextMapPropagator(),
                NatsRequestTextMapGetter.INSTANCE))
        .buildConsumerInstrumenter(NatsRequestTextMapGetter.INSTANCE);
  }

  public static Instrumenter<NatsRequest, NatsRequest> createClientInstrumenter(
      OpenTelemetry openTelemetry) {
    return Instrumenter.<NatsRequest, NatsRequest>builder(
            openTelemetry, INSTRUMENTATION_NAME, PRODUCER_SPAN_NAME_EXTRACTOR)
        .addAttributesExtractor(CLIENT_ATTRIBUTES_EXTRACTOR)
        .addSpanLinksExtractor(
            new PropagatorBasedSpanLinksExtractor<>(
                openTelemetry.getPropagators().getTextMapPropagator(),
                NatsRequestTextMapGetter.INSTANCE))
        .buildClientInstrumenter(NatsRequestTextMapSetter.INSTANCE);
  }

  private NatsInstrumenterFactory() {}
}
