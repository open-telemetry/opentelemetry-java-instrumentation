/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_26;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.redisson.client.RedisClient;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionsHolder;
import org.redisson.misc.AsyncSemaphore;

public class RedissonConnectionPoolMetrics {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.redisson-metrics-3.26";

  private static final Map<RedisClient, Registration> clientMetrics = new ConcurrentHashMap<>();

  public static void registerMetrics(
      ClientConnectionsEntry entry,
      RedisClient redisClient,
      int regularMinIdle,
      int regularMax,
      MasterSlaveServersConfig config) {
    clientMetrics.computeIfAbsent(
        redisClient,
        unused -> createRegistration(entry, redisClient, regularMinIdle, regularMax, config));
  }

  @Nullable
  private static Registration createRegistration(
      ClientConnectionsEntry entry,
      RedisClient redisClient,
      int regularMinIdle,
      int regularMax,
      MasterSlaveServersConfig config) {
    BatchCallback regularCallback =
        createCallback(
            entry.getConnectionsHolder(), redisClient, regularMinIdle, regularMax, "regular");
    BatchCallback subscriptionCallback =
        createCallback(
            entry.getPubSubConnectionsHolder(),
            redisClient,
            config.getSubscriptionConnectionMinimumIdleSize(),
            config.getSubscriptionConnectionPoolSize(),
            "subscription");
    if (regularCallback == null && subscriptionCallback == null) {
      return null;
    }
    return new Registration(regularCallback, subscriptionCallback);
  }

  @Nullable
  private static BatchCallback createCallback(
      ConnectionsHolder<?> holder,
      RedisClient redisClient,
      int minIdleConnections,
      int maxConnections,
      String poolKind) {
    if (maxConnections <= 0) {
      return null;
    }

    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            poolName(poolKind, redisClient.getAddr()));
    ObservableLongMeasurement connections = metrics.connections();
    ObservableLongMeasurement minIdle = metrics.minIdleConnections();
    ObservableLongMeasurement max = metrics.maxConnections();
    ObservableLongMeasurement pending = metrics.pendingRequestsForConnection();
    Attributes attributes = metrics.getAttributes();
    Attributes usedAttributes = metrics.getUsedConnectionsAttributes();
    Attributes idleAttributes = metrics.getIdleConnectionsAttributes();
    AsyncSemaphore semaphore = holder.getFreeConnectionsCounter();

    return metrics.batchCallback(
        () -> {
          int idleConnections = holder.getFreeConnections().size();
          int usedConnections =
              Math.min(
                  maxConnections, Math.max(0, holder.getAllConnections().size() - idleConnections));
          connections.record(usedConnections, usedAttributes);
          connections.record(idleConnections, idleAttributes);
          minIdle.record(minIdleConnections, attributes);
          max.record(maxConnections, attributes);
          pending.record(semaphore.queueSize(), attributes);
        },
        connections,
        minIdle,
        max,
        pending);
  }

  private static String poolName(String poolKind, @Nullable InetSocketAddress address) {
    StringBuilder name = new StringBuilder(poolKind).append('-');
    if (address == null) {
      return name.append("unknown").toString();
    }

    String host = address.getHostString();
    if (host.indexOf(':') >= 0) {
      name.append('[').append(host).append(']');
    } else {
      name.append(host);
    }
    return name.append(':').append(address.getPort()).toString();
  }

  public static void unregisterMetrics(RedisClient redisClient) {
    Registration registration = clientMetrics.remove(redisClient);
    if (registration != null) {
      registration.close();
    }
  }

  private static final class Registration implements AutoCloseable {
    @Nullable private final BatchCallback regularCallback;
    @Nullable private final BatchCallback subscriptionCallback;
    private boolean closed;

    private Registration(
        @Nullable BatchCallback regularCallback, @Nullable BatchCallback subscriptionCallback) {
      this.regularCallback = regularCallback;
      this.subscriptionCallback = subscriptionCallback;
    }

    @Override
    public synchronized void close() {
      if (closed) {
        return;
      }
      closed = true;
      if (regularCallback != null) {
        regularCallback.close();
      }
      if (subscriptionCallback != null) {
        subscriptionCallback.close();
      }
    }
  }

  private RedissonConnectionPoolMetrics() {}
}
