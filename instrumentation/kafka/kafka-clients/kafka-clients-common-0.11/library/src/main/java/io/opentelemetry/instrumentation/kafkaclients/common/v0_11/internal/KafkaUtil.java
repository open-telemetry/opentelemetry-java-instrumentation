/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
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

  private static final Cache<Consumer<?, ?>, Object> deliveryIdentities = Cache.weak();

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
  public static String getConsumerGroup(@Nullable Consumer<?, ?> consumer) {
    return getConsumerInfo(consumer).get(CONSUMER_GROUP);
  }

  @Nullable
  public static String getClientId(@Nullable Consumer<?, ?> consumer) {
    return getConsumerInfo(consumer).get(CLIENT_ID);
  }

  /**
   * Returns a token that identifies deliveries from the given consumer. The token is stable for the
   * lifetime of the consumer, so that a redelivery can be recognized, but it does not reference the
   * consumer, so that attaching it to consumer records does not keep the consumer alive.
   */
  @Nullable
  public static Object getDeliveryIdentity(@Nullable Consumer<?, ?> consumer) {
    if (consumer == null) {
      return null;
    }
    return deliveryIdentities.computeIfAbsent(consumer, unused -> new Object());
  }

  private static Map<String, String> getConsumerInfo(@Nullable Consumer<?, ?> consumer) {
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
    } catch (Throwable ignored) {
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

  @Nullable
  public static String serializeKey(@Nullable Object key) {
    // Calling toString() does not produce useful message-key values for byte[] or ByteBuffer.
    if (key == null || key.getClass().isArray() || key instanceof ByteBuffer) {
      return null;
    }
    return key.toString();
  }

  private KafkaUtil() {}
}
