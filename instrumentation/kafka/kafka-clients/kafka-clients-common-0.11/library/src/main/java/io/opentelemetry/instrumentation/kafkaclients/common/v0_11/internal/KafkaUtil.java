/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.Collections.emptyMap;
import static java.util.logging.Level.FINE;
import static java.util.stream.Collectors.joining;

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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.kafka.clients.Metadata;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.ClusterResource;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaUtil {

  private static final Logger logger = Logger.getLogger(KafkaUtil.class.getName());

  private static final String CONSUMER_GROUP = "consumer_group";
  private static final String CLIENT_ID = "client_id";

  private static final VirtualField<Consumer<?, ?>, Map<String, String>> CONSUMER_INFO =
      VirtualField.find(Consumer.class, Map.class);

  // Cached per-instance; resolved lazily after the first metadata refresh.
  private static final VirtualField<Consumer<?, ?>, KafkaClusterId> CONSUMER_CLUSTER_ID_FIELD =
      VirtualField.find(Consumer.class, KafkaClusterId.class);

  private static final VirtualField<Producer<?, ?>, KafkaClusterId> PRODUCER_CLUSTER_ID_FIELD =
      VirtualField.find(Producer.class, KafkaClusterId.class);

  // ClassValue: thread-safe per-class cache; Optional.empty() prevents repeated failed lookups.
  // Superclass walk needed: Kafka 3.7+ delegate classes may inherit 'metadata' from a parent.
  private static final ClassValue<Optional<Field>> metadataFieldCache =
      new ClassValue<Optional<Field>>() {
        @Override
        protected Optional<Field> computeValue(Class<?> holderClass) {
          for (Class<?> cls = holderClass; cls != null; cls = cls.getSuperclass()) {
            try {
              Field field = cls.getDeclaredField("metadata");
              field.setAccessible(true);
              return Optional.of(field);
            } catch (NoSuchFieldException ignored) {
              // 'metadata' not declared on this class; check superclass
            } catch (RuntimeException | LinkageError e) {
              // SecurityException from either call, InaccessibleObjectException from
              // setAccessible, or NoClassDefFoundError because getDeclaredField resolves the
              // types of every declared field. Reflection can't succeed for this class, so stop
              // walking and cache the miss.
              logReflectionFailureOnce(holderClass, e.toString());
              return Optional.empty();
            }
          }
          logReflectionFailureOnce(holderClass, "no 'metadata' field found");
          return Optional.empty();
        }
      };

  private static final Set<String> reflectionFailuresLogged = ConcurrentHashMap.newKeySet();

  private static final MethodHandle GET_GROUP_METADATA;
  private static final MethodHandle GET_GROUP_ID;
  private static final Field PRODUCER_CONFIG_FIELD;
  // KafkaConsumer.delegate field (Kafka 3.7+); null on pre-3.7 where there is no delegation.
  @Nullable private static final Field DELEGATE_FIELD;

  static {
    MethodHandle getGroupMetadata;
    MethodHandle getGroupId;
    Field producerConfigField;
    Field delegateField;

    try {
      Class<?> consumerGroupMetadata =
          Class.forName("org.apache.kafka.clients.consumer.ConsumerGroupMetadata");

      // Class.forName + MethodHandles lets this compile against Kafka 0.11; consumer-group
      // extraction is silently skipped on older versions via ClassNotFoundException catch.
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

    try {
      delegateField = KafkaConsumer.class.getDeclaredField("delegate");
      delegateField.setAccessible(true);
    } catch (NoSuchFieldException | RuntimeException ignored) {
      // pre-3.7: no delegate field
      delegateField = null;
    }

    GET_GROUP_METADATA = getGroupMetadata;
    GET_GROUP_ID = getGroupId;
    PRODUCER_CONFIG_FIELD = producerConfigField;
    DELEGATE_FIELD = delegateField;
  }

  @Nullable
  public static String getConsumerGroup(@Nullable Consumer<?, ?> consumer) {
    return getConsumerInfo(consumer).get(CONSUMER_GROUP);
  }

  @Nullable
  public static String getClientId(@Nullable Consumer<?, ?> consumer) {
    return getConsumerInfo(consumer).get(CLIENT_ID);
  }

  private static Map<String, String> getConsumerInfo(@Nullable Consumer<?, ?> consumer) {
    if (consumer == null) {
      return emptyMap();
    }
    Map<String, String> map = CONSUMER_INFO.get(consumer);
    if (map == null) {
      map = new HashMap<>();
      map.put(CONSUMER_GROUP, extractConsumerGroup(consumer));
      map.put(CLIENT_ID, extractClientId(consumer));
      CONSUMER_INFO.set(consumer, map);
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

  /** Pre-3.7: reads consumer's own {@code metadata} field; Kafka 3.7+: reads via delegate. */
  @Nullable
  public static String getClusterId(@Nullable Consumer<?, ?> consumer) {
    if (consumer == null || !(consumer instanceof KafkaConsumer)) {
      return null;
    }
    KafkaClusterId cached = CONSUMER_CLUSTER_ID_FIELD.get(consumer);
    if (cached != null) {
      if (cached == KafkaClusterId.UNAVAILABLE) {
        return null;
      }
      if (cached.clusterId != null) {
        return cached.clusterId;
      }
      // Pending state: cluster id not yet available from broker; retry this span.
      String id = clusterIdFromMetadata(cached.metadata);
      if (id != null) {
        CONSUMER_CLUSTER_ID_FIELD.set(consumer, KafkaClusterId.resolved(id));
      }
      return id;
    }
    return resolveAndCache(consumer, CONSUMER_CLUSTER_ID_FIELD, resolveMetadataHolder(consumer));
  }

  @Nullable
  public static String getClusterId(@Nullable Producer<?, ?> producer) {
    if (producer == null || !(producer instanceof KafkaProducer)) {
      return null;
    }
    KafkaClusterId cached = PRODUCER_CLUSTER_ID_FIELD.get(producer);
    if (cached != null) {
      if (cached == KafkaClusterId.UNAVAILABLE) {
        return null;
      }
      if (cached.clusterId != null) {
        return cached.clusterId;
      }
      // Pending state: cluster id not yet available from broker; retry this span.
      String id = clusterIdFromMetadata(cached.metadata);
      if (id != null) {
        PRODUCER_CLUSTER_ID_FIELD.set(producer, KafkaClusterId.resolved(id));
      }
      return id;
    }
    return resolveAndCache(producer, PRODUCER_CLUSTER_ID_FIELD, producer);
  }

  @Nullable
  private static <T> String resolveAndCache(
      T client, VirtualField<T, KafkaClusterId> field, @Nullable Object holder) {
    if (holder == null) {
      field.set(client, KafkaClusterId.UNAVAILABLE);
      return null;
    }
    Field metadataField = metadataField(holder.getClass());
    if (metadataField == null) {
      field.set(client, KafkaClusterId.UNAVAILABLE);
      return null;
    }
    try {
      Metadata metadata = (Metadata) metadataField.get(holder);
      if (metadata == null) {
        // Transient: field not yet initialised (shouldn't happen after construction, but be safe).
        return null;
      }
      String id = clusterIdFromMetadata(metadata);
      if (id != null) {
        field.set(client, KafkaClusterId.resolved(id));
      } else {
        field.set(client, KafkaClusterId.of(metadata));
      }
      return id;
    } catch (IllegalAccessException | ClassCastException e) {
      logReflectionFailureOnce(holder.getClass(), e.toString());
      field.set(client, KafkaClusterId.UNAVAILABLE);
      return null;
    }
  }

  private static Object resolveMetadataHolder(Consumer<?, ?> consumer) {
    if (DELEGATE_FIELD == null) {
      return consumer;
    }
    try {
      Object delegate = DELEGATE_FIELD.get(consumer);
      return delegate != null ? delegate : consumer;
    } catch (IllegalAccessException | RuntimeException ignored) {
      return consumer;
    }
  }

  @Nullable
  private static Field metadataField(Class<?> holderClass) {
    return metadataFieldCache.get(holderClass).orElse(null);
  }

  @Nullable
  static Metadata extractMetadataFromHolder(@Nullable Object holder) {
    if (holder == null) {
      return null;
    }
    Field field = metadataField(holder.getClass());
    if (field == null) {
      return null;
    }
    try {
      return (Metadata) field.get(holder);
    } catch (IllegalAccessException | ClassCastException e) {
      logReflectionFailureOnce(holder.getClass(), e.toString());
      return null;
    }
  }

  @Nullable
  public static String clusterIdFromMetadata(@Nullable Metadata metadata) {
    if (metadata == null) {
      return null;
    }
    try {
      Cluster cluster = metadata.fetch();
      if (cluster == null || cluster.isBootstrapConfigured()) {
        return null;
      }
      ClusterResource resource = cluster.clusterResource();
      if (resource == null) {
        return null;
      }
      String id = resource.clusterId();
      return (id != null && !id.isEmpty()) ? id : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static void logReflectionFailureOnce(Class<?> holderClass, String detail) {
    if (logger.isLoggable(FINE) && reflectionFailuresLogged.add(holderClass.getName())) {
      logger.log(
          FINE,
          "Unable to resolve Kafka cluster id from {0}: {1}. messaging.kafka.cluster.id will be"
              + " absent for this client type.",
          new Object[] {holderClass.getName(), detail});
    }
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
