/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisShardInfo;

public class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-1.4";

  private static final Instrumenter<JedisRequest, Void> instrumenter;

  private static final VirtualField<Connection, RedisServerTarget> CONNECTION_TARGET =
      VirtualField.find(Connection.class, RedisServerTarget.class);
  private static final ThreadLocal<RedisServerTarget> configuredTarget = new ThreadLocal<>();

  static {
    JedisDbAttributesGetter dbAttributesGetter = new JedisDbAttributesGetter();

    InstrumenterBuilder<JedisRequest, Void> builder =
        Instrumenter.<JedisRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(
                    dbAttributesGetter, GlobalOpenTelemetry.get()))
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  static Instrumenter<JedisRequest, Void> instrumenter() {
    return instrumenter;
  }

  public static void captureConnectionTarget(Connection connection) {
    RedisServerTarget target = configuredTarget.get();
    if (target == null) {
      target = RedisServerTarget.ofHostAndPort(connection.getHost(), connection.getPort());
    }
    CONNECTION_TARGET.set(connection, target);
  }

  @Nullable
  public static ConfiguredTargetScope openConfiguredTargetScope(
      @Nullable RedisServerTarget target) {
    if (target == null) {
      return null;
    }
    RedisServerTarget previous = configuredTarget.get();
    configuredTarget.set(target);
    return new ConfiguredTargetScope(previous);
  }

  @Nullable
  static RedisServerTarget connectionTarget(Connection connection) {
    RedisServerTarget target = configuredTarget.get();
    if (target != null) {
      return target;
    }
    return CONNECTION_TARGET.get(connection);
  }

  @Nullable
  public static RedisServerTarget createServerTarget(@Nullable List<JedisShardInfo> shards) {
    if (shards == null) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(shards.size());
    for (JedisShardInfo shard : shards) {
      endpoints.add(
          shard == null ? null : RedisServerTarget.endpoint(shard.getHost(), shard.getPort()));
    }
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  public static class ConfiguredTargetScope implements AutoCloseable {

    @Nullable private final RedisServerTarget previous;

    private ConfiguredTargetScope(@Nullable RedisServerTarget previous) {
      this.previous = previous;
    }

    @Override
    public void close() {
      if (previous == null) {
        configuredTarget.remove();
      } else {
        configuredTarget.set(previous);
      }
    }
  }

  private JedisSingletons() {}
}
