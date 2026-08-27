/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

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
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import redis.clients.jedis.BinaryJedis;
import redis.clients.jedis.Connection;
import redis.clients.util.Pool;
import redis.clients.util.Sharded;

public class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-2.0";

  private static final Instrumenter<JedisRequest, Void> instrumenter;

  private static final VirtualField<Connection, RedisServerTarget> CONNECTION_TARGET =
      VirtualField.find(Connection.class, RedisServerTarget.class);

  private static final VirtualField<Sharded<?, ?>, RedisServerTarget> SHARDED_TARGET =
      VirtualField.find(Sharded.class, RedisServerTarget.class);

  private static final VirtualField<Pool<?>, RedisServerTarget> POOL_TARGET =
      VirtualField.find(Pool.class, RedisServerTarget.class);

  // the cluster connection handler was added in jedis 2.4, so it has no type that spans this
  // module's whole version range and cannot carry a virtual field
  private static final Cache<Object, RedisServerTarget> clusterTargets = Cache.weak();

  static {
    JedisDbAttributesGetter dbAttributesGetter = new JedisDbAttributesGetter();
    // Redis semantic conventions don't follow the regular pattern of adding db.namespace to the
    // span name.
    JedisDbAttributesGetter spanNameAttributesGetter =
        new JedisDbAttributesGetter() {
          @Override
          @Nullable
          public String getDbNamespace(JedisRequest request) {
            return null;
          }
        };

    InstrumenterBuilder<JedisRequest, Void> builder =
        Instrumenter.<JedisRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(spanNameAttributesGetter))
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

  public static void setShardedTarget(Sharded<?, ?> sharded, @Nullable RedisServerTarget target) {
    SHARDED_TARGET.set(sharded, target);
  }

  public static void setPoolTarget(Pool<?> pool, @Nullable RedisServerTarget target) {
    POOL_TARGET.set(pool, target);
  }

  public static void setClusterTarget(Object handler, @Nullable RedisServerTarget target) {
    if (target != null) {
      clusterTargets.put(handler, target);
    }
  }

  public static void attachShardedTarget(Sharded<?, ?> sharded, @Nullable Object shard) {
    attach(SHARDED_TARGET.get(sharded), shard);
  }

  public static void attachPoolTarget(Pool<?> pool, @Nullable Object resource) {
    attach(POOL_TARGET.get(pool), resource);
  }

  public static void attachClusterTarget(Object handler, @Nullable Object connection) {
    attach(clusterTargets.get(handler), connection);
  }

  private static void attach(@Nullable RedisServerTarget target, @Nullable Object jedis) {
    if (target == null || !(jedis instanceof BinaryJedis)) {
      return;
    }
    Connection connection = ((BinaryJedis) jedis).getClient();
    if (connection != null) {
      CONNECTION_TARGET.set(connection, target);
    }
  }

  @Nullable
  static RedisServerTarget connectionTarget(Connection connection) {
    return CONNECTION_TARGET.get(connection);
  }

  private JedisSingletons() {}
}
