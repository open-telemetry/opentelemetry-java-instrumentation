/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.redis;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.vertx.core.Future;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.impl.RedisStandaloneConnection;
import io.vertx.redis.client.impl.RedisURI;
import java.util.concurrent.CompletableFuture;

public final class VertxRedisClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-redis-client-4.0";
  private static final Instrumenter<VertxRedisClientRequest, Void> INSTRUMENTER;

  private static final ThreadLocal<RedisURI> redisUriThreadLocal = new ThreadLocal<>();
  private static final VirtualField<Command, String> commandNameField =
      VirtualField.find(Command.class, String.class);
  private static final VirtualField<RedisStandaloneConnection, RedisURI> redisUriField =
      VirtualField.find(RedisStandaloneConnection.class, RedisURI.class);

  static {
    SpanNameExtractor<VertxRedisClientRequest> spanNameExtractor =
        DbClientSpanNameExtractor.create(VertxRedisClientAttributesGetter.INSTANCE);

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
                    CommonConfig.get().getPeerServiceResolver()));

    INSTRUMENTER = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<VertxRedisClientRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static <T> Future<T> wrapEndSpan(
      Future<T> future, Context context, VertxRedisClientRequest request) {
    Context parentContext = Context.current();
    CompletableFuture<T> result = new CompletableFuture<>();
    future
        .toCompletionStage()
        .whenComplete(
            (value, throwable) -> {
              instrumenter().end(context, request, null, null);
              try (Scope ignore = parentContext.makeCurrent()) {
                if (throwable != null) {
                  result.completeExceptionally(throwable);
                } else {
                  result.complete(value);
                }
              }
            });
    return Future.fromCompletionStage(result);
  }

  public static RedisURI getRedisUriThreadLocal() {
    return redisUriThreadLocal.get();
  }

  public static void setRedisUriThreadLocal(RedisURI redisUri) {
    redisUriThreadLocal.set(redisUri);
  }

  public static void setCommandName(Command command, String commandName) {
    commandNameField.set(command, commandName);
  }

  public static String getCommandName(Command command) {
    return commandNameField.get(command);
  }

  public static void setRedisUri(RedisStandaloneConnection connection, RedisURI redisUri) {
    redisUriField.set(connection, redisUri);
  }

  public static RedisURI getRedisUri(RedisStandaloneConnection connection) {
    return redisUriField.get(connection);
  }

  private VertxRedisClientSingletons() {}
}
