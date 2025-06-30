/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProducerRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.RecordMetadata;

public final class KafkaSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11";

  private static final boolean PRODUCER_PROPAGATION_ENABLED =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.kafka.producer-propagation.enabled", true);

  private static final Instrumenter<KafkaProducerRequest, RecordMetadata> PRODUCER_INSTRUMENTER;
  private static final Instrumenter<KafkaReceiveRequest, Void> CONSUMER_RECEIVE_INSTRUMENTER;
  private static final Instrumenter<KafkaProcessRequest, Void> CONSUMER_PROCESS_INSTRUMENTER;

  public static final VirtualField<Consumer<?, ?>, List<String>>
      CONSUMER_BOOTSTRAP_SERVERS_VIRTUAL_FIELD = VirtualField.find(Consumer.class, List.class);
  public static final VirtualField<Producer<?, ?>, List<String>>
      PRODUCER_BOOTSTRAP_SERVERS_VIRTUAL_FIELD = VirtualField.find(Producer.class, List.class);

  static {
    KafkaInstrumenterFactory instrumenterFactory =
        new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
            .setCaptureExperimentalSpanAttributes(
                AgentInstrumentationConfig.get()
                    .getBoolean("otel.instrumentation.kafka.experimental-span-attributes", false))
            .setMessagingReceiveInstrumentationEnabled(
                ExperimentalConfig.get().messagingReceiveInstrumentationEnabled());
    PRODUCER_INSTRUMENTER = instrumenterFactory.createProducerInstrumenter();
    CONSUMER_RECEIVE_INSTRUMENTER = instrumenterFactory.createConsumerReceiveInstrumenter();
    CONSUMER_PROCESS_INSTRUMENTER = instrumenterFactory.createConsumerProcessInstrumenter();
  }

  public static boolean isProducerPropagationEnabled() {
    return PRODUCER_PROPAGATION_ENABLED;
  }

  public static Instrumenter<KafkaProducerRequest, RecordMetadata> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  public static Instrumenter<KafkaReceiveRequest, Void> consumerReceiveInstrumenter() {
    return CONSUMER_RECEIVE_INSTRUMENTER;
  }

  public static Instrumenter<KafkaProcessRequest, Void> consumerProcessInstrumenter() {
    return CONSUMER_PROCESS_INSTRUMENTER;
  }

  private KafkaSingletons() {}
}
