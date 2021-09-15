/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.ExperimentalConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.kafka.KafkaConsumerAdditionalAttributesExtractor;
import io.opentelemetry.instrumentation.kafka.KafkaConsumerAttributesExtractor;
import io.opentelemetry.instrumentation.kafka.KafkaConsumerExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.kafka.KafkaConsumerRecordGetter;
import io.opentelemetry.instrumentation.kafka.KafkaPropagation;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public final class KafkaStreamsSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-streams-0.11";

  private static final Instrumenter<ConsumerRecord<?, ?>, Void> INSTRUMENTER = buildInstrumenter();

  private static Instrumenter<ConsumerRecord<?, ?>, Void> buildInstrumenter() {
    KafkaConsumerAttributesExtractor attributesExtractor =
        new KafkaConsumerAttributesExtractor(MessageOperation.PROCESS);
    SpanNameExtractor<ConsumerRecord<?, ?>> spanNameExtractor =
        MessagingSpanNameExtractor.create(attributesExtractor);

    InstrumenterBuilder<ConsumerRecord<?, ?>, Void> builder =
        Instrumenter.<ConsumerRecord<?, ?>, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(new KafkaConsumerAdditionalAttributesExtractor());
    if (KafkaConsumerExperimentalAttributesExtractor.isEnabled()) {
      builder.addAttributesExtractor(new KafkaConsumerExperimentalAttributesExtractor());
    }

    if (!KafkaPropagation.isPropagationEnabled()) {
      return builder.newInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else if (ExperimentalConfig.get().suppressMessagingReceiveSpans()) {
      return builder.newConsumerInstrumenter(new KafkaConsumerRecordGetter());
    } else {
      builder.addSpanLinksExtractor(
          SpanLinksExtractor.fromUpstreamRequest(
              GlobalOpenTelemetry.getPropagators(), new KafkaConsumerRecordGetter()));
      return builder.newInstrumenter(SpanKindExtractor.alwaysConsumer());
    }
  }

  public static Instrumenter<ConsumerRecord<?, ?>, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private KafkaStreamsSingletons() {}
}
