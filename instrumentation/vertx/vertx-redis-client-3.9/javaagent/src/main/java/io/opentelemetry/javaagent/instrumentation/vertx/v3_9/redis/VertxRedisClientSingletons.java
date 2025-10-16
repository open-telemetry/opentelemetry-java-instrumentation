/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.redis;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.vertx.core.Future;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;

public final class VertxRedisClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-redis-client-3.9";
  private static final Instrumenter<VertxRedisClientRequest, Void> INSTRUMENTER;

  private static final VirtualField<RedisConnection, String> connectionInfoField =
      VirtualField.find(RedisConnection.class, String.class);
  private static final ThreadLocal<RedisOptions> redisOptionsThreadLocal = new ThreadLocal<>();

  static {
    // Redis semantic conventions don't follow the regular pattern of adding the db.namespace to
    // the span name
    SpanNameExtractor<VertxRedisClientRequest> spanNameExtractor =
        VertxRedisClientRequest::getCommand;

    InstrumenterBuilder<VertxRedisClientRequest, Void> builder =
        Instrumenter.<VertxRedisClientRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(
                DbClientAttributesExtractor.create(VertxRedisClientAttributesGetter.INSTANCE))
            .addAttributesExtractor(VertxRedisClientAttributesExtractor.INSTANCE)
            .addAttributesExtractor(
                ServerAttributesExtractor.create(VertxRedisClientNetAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                NetworkAttributesExtractor.create(VertxRedisClientNetAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    VertxRedisClientNetAttributesGetter.INSTANCE,
                    AgentCommonConfig.get().getPeerServiceResolver()))
            .addOperationMetrics(DbClientMetrics.get());

    INSTRUMENTER = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<VertxRedisClientRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  @com.google.errorprone.annotations.CanIgnoreReturnValue
  public static <T> Future<T> wrapEndSpan(//todo: this method is not used?
      Future<T> future, Context context, VertxRedisClientRequest request) {
    // For 3.9, we just end the span immediately since the async handling is more complex
    instrumenter().end(context, request, null, null);
    return future;
  }

  public static void setConnectionInfo(RedisConnection connection, String connectionInfo) {
    connectionInfoField.set(connection, connectionInfo);
  }

  public static String getConnectionInfo(RedisConnection connection) {
    String info = connectionInfoField.get(connection);
    if (info == null) {
      // Fallback to RedisOptions from ThreadLocal if connection info not set
      RedisOptions options = redisOptionsThreadLocal.get();
      if (options != null) {
        info = extractConnectionInfoFromOptions(options);
        setConnectionInfo(connection, info);
      }
    }
    return info;
  }

  public static void setRedisOptions(RedisOptions options) {
    redisOptionsThreadLocal.set(options);
  }

  public static void clearRedisOptions() {//todo: this method is not used
    redisOptionsThreadLocal.remove();
  }

  private static String extractConnectionInfoFromOptions(RedisOptions options) {
    try {
      // Handle single endpoint (standalone mode)
      // Note: setConnectionString() typically sets the endpoint internally
      String endpoint = options.getEndpoint();
      if (endpoint != null && !endpoint.isEmpty()) {
        return endpoint;
      }

      // Handle multiple endpoints (cluster mode)
      if (options.getEndpoints() != null && !options.getEndpoints().isEmpty()) {
        return String.join(",", options.getEndpoints());
      }

      // Fallback - check master name for sentinel mode
      String masterName = options.getMasterName();
      if (masterName != null && !masterName.isEmpty()) {
        return "redis-sentinel://" + masterName;
      }

      return "redis://localhost:6379";
    } catch (RuntimeException e) {
      // Ignore any reflection or method access errors
      return "redis://localhost:6379";
    }
  }

  private VertxRedisClientSingletons() {}
}
