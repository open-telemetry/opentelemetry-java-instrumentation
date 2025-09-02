/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.redis;

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
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
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
    public static class AdviceScope {
      public VertxRedisClientRequest otelRequest;
      public Context context;
      public Scope scope;

      @Nullable
      public static AdviceScope start(
          RedisStandaloneConnection connection, @Nullable Request request, NetSocket netSocket) {

        if (request == null) {
          return null;
        }

        String commandName = VertxRedisClientSingletons.getCommandName(request.command());
        RedisURI redisUri = VertxRedisClientSingletons.getRedisUri(connection);
        if (commandName == null || redisUri == null) {
          return null;
        }

        VertxRedisClientRequest otelRequest =
            new VertxRedisClientRequest(
                commandName, RequestUtil.getArgs(request), redisUri, netSocket);
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, otelRequest)) {
          return null;
        }
        AdviceScope locals = new AdviceScope();
        locals.otelRequest = otelRequest;
        locals.context = instrumenter().start(parentContext, locals.otelRequest);
        locals.scope = locals.context.makeCurrent();
        return locals;
      }

      @Nullable
      public Future<Response> end(
          @Nullable Future<Response> responseFuture, @Nullable Throwable throwable) {
        if (scope == null) {
          return responseFuture;
        }

        scope.close();
        if (throwable != null) {
          instrumenter().end(context, otelRequest, null, throwable);
        } else {
          responseFuture =
              VertxRedisClientSingletons.wrapEndSpan(responseFuture, context, otelRequest);
        }
        return responseFuture;
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.This RedisStandaloneConnection connection,
        @Advice.Argument(0) @Nullable Request request,
        @Advice.FieldValue("netSocket") NetSocket netSocket) {

      return AdviceScope.start(connection, request, netSocket);
    }

    @Nullable
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Future<Response> onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Return @Nullable Future<Response> responseFuture,
        @Advice.Enter @Nullable AdviceScope adviceScope) {

      if (adviceScope != null) {
        return adviceScope.end(responseFuture, throwable);
      }

      return responseFuture;
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
