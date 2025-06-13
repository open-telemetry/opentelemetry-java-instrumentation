/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
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

  private static final KafkaTelemetry telemetry = KafkaTelemetry.create(GlobalOpenTelemetry.get());

  @Nullable private String bootstrapServers;

  @Nullable private String clientId;

  @Override
  @CanIgnoreReturnValue
  public ProducerRecord<K, V> onSend(ProducerRecord<K, V> producerRecord) {
    telemetry.buildAndInjectSpan(producerRecord, clientId, bootstrapServers);
    return producerRecord;
  }

  @Override
  public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) {}

  @Override
  public void close() {}

  @Override
  public void configure(Map<String, ?> map) {
    clientId = Objects.toString(map.get(ProducerConfig.CLIENT_ID_CONFIG), null);
    bootstrapServers = Objects.toString(map.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG), null);

    // TODO: support experimental attributes config
  }
}
