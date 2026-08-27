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
import javax.annotation.Nullable;
import redis.clients.jedis.BinaryJedis;
import redis.clients.jedis.Connection;
import redis.clients.util.Sharded;

public class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-1.4";

  private static final Instrumenter<JedisRequest, Void> instrumenter;

  private static final VirtualField<Sharded<?, ?>, RedisServerTarget> SHARDED_TARGET =
      VirtualField.find(Sharded.class, RedisServerTarget.class);

  private static final VirtualField<Connection, RedisServerTarget> CONNECTION_TARGET =
      VirtualField.find(Connection.class, RedisServerTarget.class);

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

  public static void setShardedTarget(Sharded<?, ?> sharded, @Nullable RedisServerTarget target) {
    SHARDED_TARGET.set(sharded, target);
  }

  @Nullable
  private static RedisServerTarget shardedTarget(Sharded<?, ?> sharded) {
    return SHARDED_TARGET.get(sharded);
  }

  public static void attachShardedTarget(Sharded<?, ?> sharded, @Nullable Object shard) {
    if (!(shard instanceof BinaryJedis)) {
      return;
    }
    RedisServerTarget target = shardedTarget(sharded);
    if (target != null) {
      setConnectionTarget(((BinaryJedis) shard).getClient(), target);
    }
  }

  private static void setConnectionTarget(
      @Nullable Connection connection, RedisServerTarget target) {
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
