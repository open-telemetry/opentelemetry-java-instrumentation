/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import redis.ActorRequest;
import redis.RedisClientPoolLike;
import redis.RoundRobinPoolRequest;
import redis.commands.TransactionBuilder;

public class RediscalaSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rediscala-1.8";

  private static final Instrumenter<RediscalaRequest, Void> instrumenter;

  public static final VirtualField<TransactionBuilder, ServerEndpoint> TRANSACTION_ENDPOINT =
      VirtualField.find(TransactionBuilder.class, ServerEndpoint.class);

  public static final VirtualField<TransactionBuilder, Object> TRANSACTION_CLIENT =
      VirtualField.find(TransactionBuilder.class, Object.class);

  public static final VirtualField<ActorRequest, RedisServerTarget> ACTOR_REQUEST_TARGET =
      VirtualField.find(ActorRequest.class, RedisServerTarget.class);

  public static final VirtualField<RoundRobinPoolRequest, RedisServerTarget> POOL_REQUEST_TARGET =
      VirtualField.find(RoundRobinPoolRequest.class, RedisServerTarget.class);

  public static final VirtualField<RedisClientPoolLike, RedisServerTarget> CLUSTER_TARGET =
      VirtualField.find(RedisClientPoolLike.class, RedisServerTarget.class);

  static {
    RediscalaAttributesGetter dbAttributesGetter = new RediscalaAttributesGetter();
    // Redis semantic conventions don't follow the regular pattern of adding db.namespace to the
    // span name.
    RediscalaAttributesGetter spanNameAttributesGetter =
        new RediscalaAttributesGetter() {
          @Override
          @Nullable
          public String getDbNamespace(RediscalaRequest request) {
            return null;
          }
        };

    InstrumenterBuilder<RediscalaRequest, Void> builder =
        Instrumenter.<RediscalaRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(spanNameAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<RediscalaRequest, Void> instrumenter() {
    return instrumenter;
  }

  @Nullable
  static <T> RedisServerTarget getServerTarget(
      VirtualField<T, RedisServerTarget> targetField, T client) {
    RedisServerTarget target = targetField.get(client);
    if (target == null) {
      target = RediscalaServerTargets.of(client);
      if (target != null) {
        targetField.set(client, target);
      }
    }
    return target;
  }

  private RediscalaSingletons() {}
}
