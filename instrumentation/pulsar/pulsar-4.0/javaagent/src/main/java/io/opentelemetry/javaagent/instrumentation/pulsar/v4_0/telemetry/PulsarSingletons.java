/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v4_0.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProducerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.pulsar.common.telemetry.MessageTextMapSetter;
import io.opentelemetry.javaagent.instrumentation.pulsar.common.telemetry.PulsarNetClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.pulsar.common.telemetry.PulsarRequest;

public class PulsarSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.pulsar-4.0";
  private static final OpenTelemetry TELEMETRY = GlobalOpenTelemetry.get();
  private static final Instrumenter<PulsarRequest, Void> PRODUCER_INSTRUMENTER =
      createProducerInstrumenter();

  public static Instrumenter<PulsarRequest, Void> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  private static Instrumenter<PulsarRequest, Void> createProducerInstrumenter() {
    MessagingAttributesGetter<PulsarRequest, Void> getter =
        PulsarMessagingAttributesGetter.INSTANCE;

    InstrumenterBuilder<PulsarRequest, Void> builder =
        Instrumenter.<PulsarRequest, Void>builder(
                TELEMETRY,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, MessageOperation.PUBLISH))
            .addAttributesExtractor(
                createMessagingAttributesExtractor(getter, MessageOperation.PUBLISH))
            .addAttributesExtractor(
                ServerAttributesExtractor.create(new PulsarNetClientAttributesGetter()))
            .addOperationMetrics(MessagingProducerMetrics.get());

    return builder.buildProducerInstrumenter(MessageTextMapSetter.INSTANCE);
  }

  private static <T> AttributesExtractor<T, Void> createMessagingAttributesExtractor(
      MessagingAttributesGetter<T, Void> getter, MessageOperation operation) {
    return MessagingAttributesExtractor.builder(getter, operation).build();
  }

  private PulsarSingletons() {}
}
