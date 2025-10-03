/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.OpenTelemetrySupplier;
import java.util.Map;
import java.util.Objects;
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

  public static final String CONFIG_KEY_OPENTELEMETRY_SUPPLIER = "opentelemetry.supplier";

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

    OpenTelemetry openTelemetry;
    Object openTelemetrySupplier = configs.get(CONFIG_KEY_OPENTELEMETRY_SUPPLIER);
    if (openTelemetrySupplier == null) {
      // Fallback to GlobalOpenTelemetry if not configured
      openTelemetry = GlobalOpenTelemetry.get();
    } else {
      if (!(openTelemetrySupplier instanceof OpenTelemetrySupplier)) {
        throw new IllegalStateException(
            "Configuration property "
                + CONFIG_KEY_OPENTELEMETRY_SUPPLIER
                + " is not instance of OpenTelemetrySupplier");
      }
      openTelemetry = ((OpenTelemetrySupplier) openTelemetrySupplier).get();
    }

    this.telemetry =
        KafkaTelemetry.builder(openTelemetry)
            .setCapturedHeaders(
                ConfigPropertiesUtil.getList(
                    "otel.instrumentation.messaging.experimental.capture-headers", emptyList()))
            .build();
  }
}
