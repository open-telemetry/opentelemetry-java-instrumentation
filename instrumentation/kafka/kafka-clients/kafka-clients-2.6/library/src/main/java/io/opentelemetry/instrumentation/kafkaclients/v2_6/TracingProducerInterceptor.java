/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * A ProducerInterceptor that adds tracing capability. Add this interceptor's class name or class
 * via ProducerConfig.INTERCEPTOR_CLASSES_CONFIG property to your Producer's properties to get it
 * instantiated and used. See more details on ProducerInterceptor usage in its Javadoc.
 */
public class TracingProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

  @Nullable private KafkaTelemetry telemetry;
  @Nullable private String clientId;

  @Override
  @CanIgnoreReturnValue
  public ProducerRecord<K, V> onSend(ProducerRecord<K, V> producerRecord) {
    if (telemetry != null) {
      telemetry.buildAndInjectSpan(producerRecord, clientId);
    }
    return producerRecord;
  }

  @Override
  public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) {}

  @Override
  public void close() {}

  @Override
  public void configure(Map<String, ?> configs) {
    clientId = Objects.toString(configs.get(ProducerConfig.CLIENT_ID_CONFIG), null);

    // Try to get KafkaTelemetry from config
    Object telemetrySupplier = configs.get(KafkaTelemetry.CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER);
    if (telemetrySupplier instanceof Supplier) {
      Object kafkaTelemetry = ((Supplier<?>) telemetrySupplier).get();
      if (kafkaTelemetry instanceof KafkaTelemetry) {
        this.telemetry = (KafkaTelemetry) kafkaTelemetry;
        return;
      }
    }

    // Fallback to GlobalOpenTelemetry if not configured
    this.telemetry =
        KafkaTelemetry.builder(GlobalOpenTelemetry.get())
            .setCapturedHeaders(
                ConfigPropertiesUtil.getList(
                    "otel.instrumentation.messaging.experimental.capture-headers", emptyList()))
            .build();
  }
}
