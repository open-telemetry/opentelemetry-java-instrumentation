/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * A ProducerInterceptor that adds OpenTelemetry instrumentation. Add this interceptor's class name
 * or class via ProducerConfig.INTERCEPTOR_CLASSES_CONFIG property to your Producer's properties to
 * get it instantiated and used. See more details on ProducerInterceptor usage in its Javadoc.
 *
 * <p>To configure the interceptor, use {@link KafkaTelemetry#producerInterceptorConfigProperties}
 * to obtain the configuration properties and add them to your producer configuration.
 *
 * @see KafkaTelemetry#producerInterceptorConfigProperties()
 */
public class OpenTelemetryProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

  public static final String CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER =
      "opentelemetry.kafka-telemetry.supplier";

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

    Object telemetrySupplier = configs.get(CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER);
    if (telemetrySupplier == null) {
      return;
    }

    if (!(telemetrySupplier instanceof Supplier)) {
      throw new IllegalStateException(
          "Configuration property "
              + CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER
              + " is not instance of Supplier");
    }

    Object kafkaTelemetry = ((Supplier<?>) telemetrySupplier).get();
    if (!(kafkaTelemetry instanceof KafkaTelemetry)) {
      throw new IllegalStateException(
          "Configuration property "
              + CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER
              + " supplier does not return KafkaTelemetry instance");
    }

    this.telemetry = (KafkaTelemetry) kafkaTelemetry;
  }
}
