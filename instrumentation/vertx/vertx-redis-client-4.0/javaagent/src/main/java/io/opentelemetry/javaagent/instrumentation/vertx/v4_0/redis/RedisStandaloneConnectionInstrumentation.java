/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.redis;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.redis.VertxRedisClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Future;
import io.vertx.core.net.NetSocket;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.Response;
import io.vertx.redis.client.impl.RedisStandaloneConnection;
import io.vertx.redis.client.impl.RedisURI;
import io.vertx.redis.client.impl.RequestUtil;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RedisStandaloneConnectionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.redis.client.impl.RedisStandaloneConnection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("send"), this.getClass().getName() + "$SendAdvice");
    transformer.applyAdviceToMethod(
        isConstructor(), this.getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This RedisStandaloneConnection connection,
        @Advice.Argument(0) Request request,
        @Advice.FieldValue("netSocket") NetSocket netSocket,
        @Advice.Local("otelRequest") VertxRedisClientRequest otelRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (request == null) {
        return;
      }

      String commandName = VertxRedisClientSingletons.getCommandName(request.command());
      RedisURI redisUri = VertxRedisClientSingletons.getRedisUri(connection);
      if (commandName == null || redisUri == null) {
        return;
      }

      otelRequest =
          new VertxRedisClientRequest(
              commandName, RequestUtil.getArgs(request), redisUri, netSocket);
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, otelRequest)) {
        return;
      }

      context = instrumenter().start(parentContext, otelRequest);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Return(readOnly = false) Future<Response> responseFuture,
        @Advice.Local("otelRequest") VertxRedisClientRequest otelRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      if (throwable != null) {
        instrumenter().end(context, otelRequest, null, throwable);
      } else {
        responseFuture =
            VertxRedisClientSingletons.wrapEndSpan(responseFuture, context, otelRequest);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This RedisStandaloneConnection connection) {
      // used in 4.1.0, for 4.0.0 it is set in RedisConnectionProviderInstrumentation
      VertxRedisClientSingletons.setRedisUri(
          connection, VertxRedisClientSingletons.getRedisUriThreadLocal());
    }
  }
}
