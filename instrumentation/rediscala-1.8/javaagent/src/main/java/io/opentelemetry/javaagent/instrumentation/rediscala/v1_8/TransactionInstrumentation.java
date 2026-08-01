/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.TRANSACTION_ENDPOINT;
import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.Operation;
import redis.commands.TransactionBuilder;
import scala.collection.immutable.Queue;
import scala.concurrent.Future;

class TransactionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.commands.TransactionBuilder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("exec").and(returns(named("scala.concurrent.Future"))),
        getClass().getName() + "$ExecAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecAdvice {
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
      public static AdviceScope start(TransactionBuilder transactionBuilder) {
        Queue<Operation<?, ?>> operations = transactionBuilder.operations().result();
        ServerEndpoint endpoint = TRANSACTION_ENDPOINT.get(transactionBuilder);
        RediscalaRequest request =
            RediscalaRequest.createTransaction(
                operations,
                endpoint != null ? endpoint.getHost() : null,
                endpoint != null ? endpoint.getPort() : null);
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }

        Context context = instrumenter().start(parentContext, request);
        return new AdviceScope(context, context.makeCurrent(), request);
      }

      public void end(
          TransactionBuilder transactionBuilder,
          @Nullable Future<Object> responseFuture,
          @Nullable Throwable throwable) {
        scope.close();
        if (throwable != null || responseFuture == null) {
          instrumenter().end(context, request, null, throwable);
        } else {
          responseFuture.onComplete(
              new OnCompleteHandler(context, request), transactionBuilder.executionContext());
        }
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(@Advice.This TransactionBuilder transactionBuilder) {
      return AdviceScope.start(transactionBuilder);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This TransactionBuilder transactionBuilder,
        @Advice.Enter @Nullable AdviceScope adviceScope,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Return @Nullable Future<Object> responseFuture) {
      if (adviceScope != null) {
        adviceScope.end(transactionBuilder, responseFuture, throwable);
      }
    }
  }
}
