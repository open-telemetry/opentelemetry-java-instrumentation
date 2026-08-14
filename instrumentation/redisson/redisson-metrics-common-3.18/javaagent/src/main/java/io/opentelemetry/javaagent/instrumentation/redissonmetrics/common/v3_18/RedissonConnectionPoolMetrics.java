/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.common.v3_18;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import org.redisson.client.RedisClient;

public class RedissonConnectionPoolMetrics {

  private static final Map<RedisClient, Registration> clientMetrics = new ConcurrentHashMap<>();

  public static void registerMetrics(
      String instrumentationName,
      RedisClient redisClient,
      ConnectionPoolMetricsSource regular,
      ConnectionPoolMetricsSource subscription) {
    clientMetrics.computeIfAbsent(
        redisClient,
        unused -> createRegistration(instrumentationName, redisClient, regular, subscription));
  }

  @Nullable
  private static Registration createRegistration(
      String instrumentationName,
      RedisClient redisClient,
      ConnectionPoolMetricsSource regular,
      ConnectionPoolMetricsSource subscription) {
    BatchCallback regularCallback = createCallback(instrumentationName, redisClient, regular);
    BatchCallback subscriptionCallback =
        createCallback(instrumentationName, redisClient, subscription);
    if (regularCallback == null && subscriptionCallback == null) {
      return null;
    }
    return new Registration(regularCallback, subscriptionCallback);
  }

  @Nullable
  private static BatchCallback createCallback(
      String instrumentationName, RedisClient redisClient, ConnectionPoolMetricsSource source) {
    if (source.maxConnections <= 0) {
      return null;
    }

    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            poolName(source.poolKind, redisClient.getAddr()));
    ObservableLongMeasurement connections = metrics.connections();
    ObservableLongMeasurement minIdle = metrics.minIdleConnections();
    ObservableLongMeasurement max = metrics.maxConnections();
    Attributes attributes = metrics.getAttributes();
    Attributes usedAttributes = metrics.getUsedConnectionsAttributes();
    Attributes idleAttributes = metrics.getIdleConnectionsAttributes();
    ObservableLongMeasurement pending = metrics.pendingRequestsForConnection();
    return metrics.batchCallback(
        () -> {
          int idleConnections = source.idleConnections.getAsInt();
          int usedConnections = source.usedConnections.applyAsInt(idleConnections);
          connections.record(
              Math.min(source.maxConnections, Math.max(0, usedConnections)), usedAttributes);
          connections.record(Math.max(0, idleConnections), idleAttributes);
          minIdle.record(source.minIdleConnections, attributes);
          max.record(source.maxConnections, attributes);
          pending.record(source.pendingRequests.getAsInt(), attributes);
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

  public static class ConnectionPoolMetricsSource {
    private final String poolKind;
    private final int minIdleConnections;
    private final int maxConnections;
    private final IntUnaryOperator usedConnections;
    private final IntSupplier idleConnections;
    private final IntSupplier pendingRequests;

    private ConnectionPoolMetricsSource(
        String poolKind,
        int minIdleConnections,
        int maxConnections,
        IntUnaryOperator usedConnections,
        IntSupplier idleConnections,
        IntSupplier pendingRequests) {
      this.poolKind = poolKind;
      this.minIdleConnections = minIdleConnections;
      this.maxConnections = maxConnections;
      this.usedConnections = usedConnections;
      this.idleConnections = idleConnections;
      this.pendingRequests = pendingRequests;
    }

    public static ConnectionPoolMetricsSource create(
        String poolKind,
        int minIdleConnections,
        int maxConnections,
        IntUnaryOperator usedConnections,
        IntSupplier idleConnections,
        IntSupplier pendingRequests) {
      return new ConnectionPoolMetricsSource(
          poolKind,
          minIdleConnections,
          maxConnections,
          usedConnections,
          idleConnections,
          pendingRequests);
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
