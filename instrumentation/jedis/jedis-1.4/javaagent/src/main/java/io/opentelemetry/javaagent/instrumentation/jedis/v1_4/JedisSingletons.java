/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;
import static java.util.logging.Level.FINE;

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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.util.Sharded;

public class JedisSingletons {
  private static final Logger logger = Logger.getLogger(JedisSingletons.class.getName());

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-1.4";

  private static final Instrumenter<JedisRequest, Void> instrumenter;

  private static final VirtualField<Connection, RedisServerTarget> CONNECTION_TARGET =
      VirtualField.find(Connection.class, RedisServerTarget.class);
  @Nullable private static final Method SHARD_INFO_GET_RESOURCE = findShardInfoGetResource();

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
    CONNECTION_TARGET.set(
        connection, RedisServerTarget.ofHostAndPort(connection.getHost(), connection.getPort()));
  }

  public static void captureShardedConnectionTargets(
      Sharded<?, ?> sharded, @Nullable List<JedisShardInfo> shards) {
    RedisServerTarget target = createServerTarget(shards);
    if (target == null) {
      return;
    }

    for (Object shard : sharded.getAllShards()) {
      Jedis jedis = getJedis(shard);
      if (jedis != null) {
        CONNECTION_TARGET.set(jedis.getClient(), target);
      }
    }
  }

  @Nullable
  static RedisServerTarget connectionTarget(Connection connection) {
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

  @Nullable
  private static Jedis getJedis(Object shard) {
    if (shard instanceof Jedis) {
      return (Jedis) shard;
    }
    if (!(shard instanceof JedisShardInfo) || SHARD_INFO_GET_RESOURCE == null) {
      return null;
    }

    try {
      Object resource = SHARD_INFO_GET_RESOURCE.invoke(shard);
      return resource instanceof Jedis ? (Jedis) resource : null;
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to obtain Jedis shard resource", e);
      return null;
    }
  }

  @Nullable
  private static Method findShardInfoGetResource() {
    try {
      return JedisShardInfo.class.getMethod("getResource");
    } catch (NoSuchMethodException | SecurityException ignored) {
      return null;
    }
  }

  private JedisSingletons() {}
}
