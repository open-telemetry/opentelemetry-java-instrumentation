/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.stream.Collectors.joining;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaUtil {

  private static final String CONSUMER_GROUP = "consumer_group";
  private static final String CLIENT_ID = "client_id";

  private static final VirtualField<Consumer<?, ?>, Map<String, String>> consumerInfoField =
      VirtualField.find(Consumer.class, Map.class);

  private static final MethodHandle GET_GROUP_METADATA;
  private static final MethodHandle GET_GROUP_ID;
  private static final Field PRODUCER_CONFIG_FIELD;

  static {
    MethodHandle getGroupMetadata;
    MethodHandle getGroupId;
    Field producerConfigField;

    try {
      Class<?> consumerGroupMetadata =
          Class.forName("org.apache.kafka.clients.consumer.ConsumerGroupMetadata");

      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      getGroupMetadata =
          lookup.findVirtual(
              Consumer.class, "groupMetadata", MethodType.methodType(consumerGroupMetadata));
      getGroupId =
          lookup.findVirtual(consumerGroupMetadata, "groupId", MethodType.methodType(String.class));

      producerConfigField = KafkaProducer.class.getDeclaredField("producerConfig");
      producerConfigField.setAccessible(true);
    } catch (ClassNotFoundException
        | IllegalAccessException
        | NoSuchMethodException
        | NoSuchFieldException ignored) {
      getGroupMetadata = null;
      getGroupId = null;
      producerConfigField = null;
    }

    GET_GROUP_METADATA = getGroupMetadata;
    GET_GROUP_ID = getGroupId;
    PRODUCER_CONFIG_FIELD = producerConfigField;
  }

  @Nullable
  public static String getConsumerGroup(Consumer<?, ?> consumer) {
    return getConsumerInfo(consumer).get(CONSUMER_GROUP);
  }

  @Nullable
  public static String getClientId(Consumer<?, ?> consumer) {
    return getConsumerInfo(consumer).get(CLIENT_ID);
  }

  private static Map<String, String> getConsumerInfo(Consumer<?, ?> consumer) {
    if (consumer == null) {
      return emptyMap();
    }
    Map<String, String> map = consumerInfoField.get(consumer);
    if (map == null) {
      map = new HashMap<>();
      map.put(CONSUMER_GROUP, extractConsumerGroup(consumer));
      map.put(CLIENT_ID, extractClientId(consumer));
      consumerInfoField.set(consumer, map);
    }
    return map;
  }

  @Nullable
  private static String extractConsumerGroup(Consumer<?, ?> consumer) {
    if (GET_GROUP_METADATA == null || GET_GROUP_ID == null) {
      return null;
    }
    try {
      Object metadata = GET_GROUP_METADATA.invoke(consumer);
      return (String) GET_GROUP_ID.invoke(metadata);
    } catch (Throwable t) {
      return null;
    }
  }

  @Nullable
  private static String extractClientId(Consumer<?, ?> consumer) {
    try {
      Map<MetricName, ? extends Metric> metrics = consumer.metrics();
      Iterator<MetricName> metricIterator = metrics.keySet().iterator();
      return metricIterator.hasNext() ? metricIterator.next().tags().get("client-id") : null;
    } catch (RuntimeException ignored) {
      // ExceptionHandlingTest uses a Consumer that throws exception on every method call
      return null;
    }
  }

  @Nullable
  public static String extractBootstrapServers(Producer<?, ?> producer) {
    if (PRODUCER_CONFIG_FIELD == null || !KafkaProducer.class.equals(producer.getClass())) {
      return null;
    }
    try {
      ProducerConfig producerConfig = (ProducerConfig) PRODUCER_CONFIG_FIELD.get(producer);
      return extractBootstrapServers(
          producerConfig.getList(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
    } catch (IllegalAccessException | IllegalArgumentException ignored) {
      return null;
    }
  }

  @Nullable
  public static String extractBootstrapServers(@Nullable List<String> serversConfig) {
    if (serversConfig == null) {
      return null;
    }
    return serversConfig.stream().map(Object::toString).collect(joining(","));
  }

  private KafkaUtil() {}
}
