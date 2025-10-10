/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaHelper;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * A ProducerInterceptor that adds OpenTelemetry instrumentation.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class OpenTelemetryProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

  public static final String CONFIG_KEY_KAFKA_HELPER_SUPPLIER =
      "opentelemetry.kafka-helper.supplier";

  @Nullable private KafkaHelper helper;
  @Nullable private String clientId;

  @Override
  @CanIgnoreReturnValue
  public ProducerRecord<K, V> onSend(ProducerRecord<K, V> producerRecord) {
    if (helper != null) {
      helper.buildAndInjectSpan(producerRecord, clientId);
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

    KafkaHelperSupplier supplier =
        getProperty(configs, CONFIG_KEY_KAFKA_HELPER_SUPPLIER, KafkaHelperSupplier.class);
    this.helper = supplier.get();
  }

  @SuppressWarnings("unchecked")
  private static <T> T getProperty(Map<String, ?> configs, String key, Class<T> requiredType) {
    Object value = configs.get(key);
    if (value == null) {
      throw new IllegalStateException("Missing required configuration property: " + key);
    }
    if (!requiredType.isInstance(value)) {
      throw new IllegalStateException(
          "Configuration property " + key + " is not instance of " + requiredType.getSimpleName());
    }
    return (T) value;
  }
}
