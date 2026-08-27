/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSocketFactory;
import redis.clients.jedis.util.Pool;

public class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-4.0";

  private static final Instrumenter<JedisRequest, Void> instrumenter;
  private static final VirtualField<Connection, JedisConnectionInfo> CONNECTION_INFO =
      VirtualField.find(Connection.class, JedisConnectionInfo.class);

  private static final VirtualField<Connection, RedisServerTarget> CONNECTION_TARGET =
      VirtualField.find(Connection.class, RedisServerTarget.class);

  private static final VirtualField<Pool<?>, RedisServerTarget> POOL_TARGET =
      VirtualField.find(Pool.class, RedisServerTarget.class);

  // the connection provider interface was renamed between jedis 4.0.0-beta1 and 4.0.0, so it has no
  // type that spans this module's whole version range and cannot carry a virtual field
  private static final Cache<Object, RedisServerTarget> providerTargets = Cache.weak();

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
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<JedisRequest, Void> instrumenter() {
    return instrumenter;
  }

  @Nullable
  static JedisConnectionInfo connectionInfo(Connection connection) {
    return CONNECTION_INFO.get(connection);
  }

  public static void setConnectionInfo(
      Connection connection, JedisSocketFactory socketFactory, @Nullable Object clientConfig) {
    CONNECTION_INFO.set(connection, JedisConnectionInfo.create(socketFactory, clientConfig));
  }

  public static void setPoolTarget(Pool<?> pool, @Nullable RedisServerTarget target) {
    POOL_TARGET.set(pool, target);
  }

  public static void setProviderTarget(Object provider, @Nullable RedisServerTarget target) {
    if (target != null) {
      providerTargets.put(provider, target);
    }
  }

  public static void attachPoolTarget(Pool<?> pool, @Nullable Object resource) {
    RedisServerTarget target = POOL_TARGET.get(pool);
    if (target != null && resource instanceof Jedis) {
      setConnectionTarget(((Jedis) resource).getConnection(), target);
    }
  }

  public static void attachProviderTarget(Object provider, @Nullable Connection connection) {
    setConnectionTarget(connection, providerTargets.get(provider));
  }

  private static void setConnectionTarget(
      @Nullable Connection connection, @Nullable RedisServerTarget target) {
    if (connection != null && target != null) {
      CONNECTION_TARGET.set(connection, target);
    }
  }

  @Nullable
  static RedisServerTarget connectionTarget(Connection connection) {
    return CONNECTION_TARGET.get(connection);
  }

  private JedisSingletons() {}
}
