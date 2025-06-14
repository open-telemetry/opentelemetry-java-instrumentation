/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.Node;

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

  private static final VirtualField<Consumer<?, ?>, String> consumerVirtualField =
      VirtualField.find(Consumer.class, String.class);
  private static final VirtualField<Producer<?, ?>, String> producerVirtualField =
      VirtualField.find(Producer.class, String.class);

  static {
    MethodHandle getGroupMetadata;
    MethodHandle getGroupId;

    try {
      Class<?> consumerGroupMetadata =
          Class.forName("org.apache.kafka.clients.consumer.ConsumerGroupMetadata");

      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      getGroupMetadata =
          lookup.findVirtual(
              Consumer.class, "groupMetadata", MethodType.methodType(consumerGroupMetadata));
      getGroupId =
          lookup.findVirtual(consumerGroupMetadata, "groupId", MethodType.methodType(String.class));
    } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException ignored) {
      getGroupMetadata = null;
      getGroupId = null;
    }

    GET_GROUP_METADATA = getGroupMetadata;
    GET_GROUP_ID = getGroupId;
  }

  @Nullable
  public static String getConsumerGroup(Consumer<?, ?> consumer) {
    return getConsumerInfo(consumer).get(CONSUMER_GROUP);
  }

  @Nullable
  public static String getClientId(Consumer<?, ?> consumer) {
    return getConsumerInfo(consumer).get(CLIENT_ID);
  }

  @Nullable
  public static String getBootstrapServers(Consumer<?, ?> consumer) {
    String bootstrapServers = consumerVirtualField.get(consumer);
    // If bootstrap servers are not available from virtual field (library instrumentation),
    // try to extract them via reflection from Kafka client's metadata
    if (bootstrapServers == null) {
      bootstrapServers = extractBootstrapServersViaReflection(consumer);
      if (bootstrapServers != null) {
        consumerVirtualField.set(consumer, bootstrapServers);
      }
    }
    return bootstrapServers;
  }

  @Nullable
  public static String getBootstrapServers(Producer<?, ?> producer) {
    String bootstrapServers = producerVirtualField.get(producer);
    // If bootstrap servers are not available from virtual field (library instrumentation),
    // try to extract them via reflection from Kafka client's metadata
    if (bootstrapServers == null) {
      bootstrapServers = extractBootstrapServersViaReflection(producer);
      if (bootstrapServers != null) {
        producerVirtualField.set(producer, bootstrapServers);
      }
    }
    return bootstrapServers;
  }

  /**
   * Extract bootstrap servers from Kafka client's metadata using reflection. 1.metadata -> Cluster
   * -> nodes 2.metadata -> MetadataCache -> nodes 3.metadata -> MetadataSnapshot -> nodes
   * 4.delegate -> metadata -> ...
   */
  @Nullable
  private static String extractBootstrapServersViaReflection(Object client) {
    if (client == null) {
      return null;
    }
    try {
      Object metadata = extractMetadata(client);

      if (metadata == null) {
        return null;
      }

      String bootstrapServers = extractFromMetadataSnapshot(metadata);

      if (bootstrapServers == null) {
        bootstrapServers = extractFromMetadataCache(metadata);
      }
      if (bootstrapServers == null) {
        bootstrapServers = extractFromCluster(metadata);
      }

      return bootstrapServers;
    } catch (RuntimeException e) {
      return null;
    }
  }

  @Nullable
  private static Object extractMetadata(Object client) {
    try {
      java.lang.reflect.Field metadataField = findField(client.getClass(), "metadata");
      if (metadataField != null) {
        metadataField.setAccessible(true);
        Object metadata = metadataField.get(client);
        if (metadata != null) {
          return metadata;
        }
      }

      java.lang.reflect.Field delegateField = findField(client.getClass(), "delegate");
      if (delegateField != null) {
        delegateField.setAccessible(true);
        Object delegate = delegateField.get(client);
        if (delegate != null) {
          java.lang.reflect.Field delegateMetadataField =
              findField(delegate.getClass(), "metadata");
          if (delegateMetadataField != null) {
            delegateMetadataField.setAccessible(true);
            return delegateMetadataField.get(delegate);
          }
        }
      }

      return null;
    } catch (Exception e) {
      return null;
    }
  }

  @Nullable
  private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
    Class<?> currentClass = clazz;
    while (currentClass != null) {
      try {
        return currentClass.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        // Field not found in current class, try parent class
        currentClass = currentClass.getSuperclass();
      }
    }
    return null;
  }

  @Nullable
  private static String extractFromCluster(Object metadata) {
    try {
      java.lang.reflect.Field clusterField = findField(metadata.getClass(), "cluster");
      if (clusterField == null) {
        return null;
      }
      clusterField.setAccessible(true);
      Object cluster = clusterField.get(metadata);

      if (cluster == null) {
        return null;
      }

      java.lang.reflect.Method nodesMethod = cluster.getClass().getDeclaredMethod("nodes");
      nodesMethod.setAccessible(true);
      Object nodes = nodesMethod.invoke(cluster);

      return formatNodesAsBootstrapServers(nodes);
    } catch (Exception e) {
      return null;
    }
  }

  @Nullable
  private static String extractFromMetadataCache(Object metadata) {
    try {
      java.lang.reflect.Field cacheField = findField(metadata.getClass(), "cache");
      if (cacheField == null) {
        return null;
      }
      cacheField.setAccessible(true);
      Object cache = cacheField.get(metadata);

      if (cache == null) {
        return null;
      }

      java.lang.reflect.Field nodesField = cache.getClass().getDeclaredField("nodes");
      nodesField.setAccessible(true);
      Object nodes = nodesField.get(cache);

      return formatNodesAsBootstrapServers(nodes);
    } catch (Exception e) {
      return null;
    }
  }

  @Nullable
  private static String extractFromMetadataSnapshot(Object metadata) {
    try {
      java.lang.reflect.Field snapshotField = findField(metadata.getClass(), "metadataSnapshot");
      if (snapshotField == null) {
        return null;
      }
      snapshotField.setAccessible(true);
      Object snapshot = snapshotField.get(metadata);

      if (snapshot == null) {
        return null;
      }

      java.lang.reflect.Field nodesField = snapshot.getClass().getDeclaredField("nodes");
      nodesField.setAccessible(true);
      Object nodes = nodesField.get(snapshot);

      return formatNodesAsBootstrapServers(nodes);
    } catch (Exception e) {
      return null;
    }
  }

  @Nullable
  private static String formatNodesAsBootstrapServers(Object nodes) {
    if (nodes == null) {
      return null;
    }

    try {
      StringBuilder sb = new StringBuilder();

      if (nodes instanceof java.util.Map) {
        // nodes is Map<Integer, Node>
        @SuppressWarnings("unchecked")
        java.util.Map<Integer, Object> nodeMap = (java.util.Map<Integer, Object>) nodes;

        for (Object node : nodeMap.values()) {
          String address = getNodeAddress(node);
          if (address != null) {
            if (sb.length() > 0) {
              sb.append(",");
            }
            sb.append(address);
          }
        }
      } else if (nodes instanceof java.util.Collection) {
        // nodes is Collection<Node>
        @SuppressWarnings("unchecked")
        java.util.Collection<Object> nodeCollection = (java.util.Collection<Object>) nodes;

        for (Object node : nodeCollection) {
          String address = getNodeAddress(node);
          if (address != null) {
            if (sb.length() > 0) {
              sb.append(",");
            }
            sb.append(address);
          }
        }
      }

      return sb.length() > 0 ? sb.toString() : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  @Nullable
  private static String getNodeAddress(Object o) {
    if (o == null || !(o instanceof Node)) {
      return null;
    }
    Node node = (Node) o;
    return node.host() + ":" + node.port();
  }

  private static Map<String, String> getConsumerInfo(Consumer<?, ?> consumer) {
    if (consumer == null) {
      return Collections.emptyMap();
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
    if (consumer == null) {
      return null;
    }
    try {
      Object metadata = GET_GROUP_METADATA.invoke(consumer);
      return (String) GET_GROUP_ID.invoke(metadata);
    } catch (Throwable e) {
      return null;
    }
  }

  @Nullable
  private static String extractClientId(Consumer<?, ?> consumer) {
    try {
      Map<MetricName, ? extends Metric> metrics = consumer.metrics();
      Iterator<MetricName> metricIterator = metrics.keySet().iterator();
      return metricIterator.hasNext() ? metricIterator.next().tags().get("client-id") : null;
    } catch (RuntimeException exception) {
      // ExceptionHandlingTest uses a Consumer that throws exception on every method call
      return null;
    }
  }

  private KafkaUtil() {}
}
