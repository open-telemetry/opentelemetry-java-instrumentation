/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
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
import redis.clients.jedis.Connection;
import redis.clients.util.Pool;

public class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-2.0";

  private static final Instrumenter<JedisRequest, Void> instrumenter;

  private static final VirtualField<Connection, RedisServerTarget> CONNECTION_TARGET =
      VirtualField.find(Connection.class, RedisServerTarget.class);

  private static final VirtualField<Pool<?>, RedisServerTarget> POOL_TARGET =
      VirtualField.find(Pool.class, RedisServerTarget.class);

  private static final ContextKey<RedisServerTarget> CURRENT_CONFIGURED_TARGET =
      ContextKey.named("opentelemetry-jedis-configured-target");

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

  public static void captureConnectionTarget(Connection connection) {
    RedisServerTarget target = Context.current().get(CURRENT_CONFIGURED_TARGET);
    if (target == null) {
      target = RedisServerTarget.ofHostAndPort(connection.getHost(), connection.getPort());
    }
    if (target != null) {
      CONNECTION_TARGET.set(connection, target);
    }
  }

  public static void capturePoolTarget(Pool<?> pool) {
    RedisServerTarget target = Context.current().get(CURRENT_CONFIGURED_TARGET);
    if (target != null) {
      POOL_TARGET.set(pool, target);
    }
  }

  public static void setPoolTarget(Pool<?> pool, @Nullable RedisServerTarget target) {
    POOL_TARGET.set(pool, target);
  }

  @Nullable
  public static Scope openPoolTargetScope(Pool<?> pool) {
    return openConfiguredTargetScope(POOL_TARGET.get(pool));
  }

  @Nullable
  public static Scope openConfiguredTargetScope(@Nullable RedisServerTarget target) {
    return target != null
        ? Context.current().with(CURRENT_CONFIGURED_TARGET, target).makeCurrent()
        : null;
  }

  @Nullable
  static RedisServerTarget connectionTarget(Connection connection) {
    RedisServerTarget target = Context.current().get(CURRENT_CONFIGURED_TARGET);
    if (target != null) {
      return target;
    }
    return CONNECTION_TARGET.get(connection);
  }

  private JedisSingletons() {}
}
