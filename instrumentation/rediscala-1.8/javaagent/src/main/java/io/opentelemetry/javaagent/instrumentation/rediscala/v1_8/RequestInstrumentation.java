/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.ActorRequest;
import redis.BufferedRequest;
import redis.RedisCommand;
import redis.Request;
import redis.RoundRobinPoolRequest;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

class RequestInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("redis.Request");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(
        namedOneOf(
            "redis.ActorRequest",
            "redis.Request",
            "redis.BufferedRequest",
            "redis.RoundRobinPoolRequest"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("send"))
            .and(takesArgument(0, named("redis.RedisCommand")))
            .and(returns(named("scala.concurrent.Future"))),
        getClass().getName() + "$SendAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;
      private final RediscalaRequest request;

      private AdviceScope(Context context, Scope scope, RediscalaRequest request) {
        this.context = context;
        this.scope = scope;
        this.request = request;
      }

      @Nullable
      public static AdviceScope start(Object action, RedisCommand<?, ?> cmd) {
        if (action instanceof BufferedRequest) {
          // TransactionInstrumentation records the span when exec() sends the batch.
          return null;
        }

        RediscalaRequest request = RediscalaRequest.create(cmd);
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }

        Context context = instrumenter().start(parentContext, request);
        return new AdviceScope(context, context.makeCurrent(), request);
      }

      public void end(
          Object action, @Nullable Future<Object> responseFuture, @Nullable Throwable throwable) {
        scope.close();

        ExecutionContext ctx = null;
        if (action instanceof ActorRequest) {
          ctx = ((ActorRequest) action).executionContext();
        } else if (action instanceof Request) {
          ctx = ((Request) action).executionContext();
        } else if (action instanceof BufferedRequest) {
          ctx = ((BufferedRequest) action).executionContext();
        } else if (action instanceof RoundRobinPoolRequest) {
          ctx = ((RoundRobinPoolRequest) action).executionContext();
        }

        if (throwable != null || responseFuture == null) {
          instrumenter().end(context, request, null, throwable);
        } else {
          responseFuture.onComplete(new OnCompleteHandler(context, request), ctx);
        }
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This Object action, @Advice.Argument(0) RedisCommand<?, ?> cmd) {
      return AdviceScope.start(action, cmd);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object action,
        @Advice.Argument(0) RedisCommand<?, ?> cmd,
        @Advice.Enter @Nullable AdviceScope adviceScope,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Return @Nullable Future<Object> responseFuture) {
      if (adviceScope != null) {
        adviceScope.end(action, responseFuture, throwable);
      }
    }
  }
}
