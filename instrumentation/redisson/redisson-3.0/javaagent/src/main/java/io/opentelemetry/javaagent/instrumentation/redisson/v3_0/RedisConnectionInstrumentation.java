/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.redisson.v3_0.RedissonSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.EndOperationListener;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.PromiseWrapper;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonBatchSpanManager;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonRequest;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.client.RedisConnection;

class RedisConnectionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.redisson.client.RedisConnection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("send"), getClass().getName() + "$SendAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    public static class AdviceScope {
      private final RedisConnection connection;
      @Nullable private final RedissonRequest request;
      @Nullable private final Context context;
      @Nullable private final Scope scope;
      private final boolean multiSpan;
      private final boolean suppressedSpan;

      private AdviceScope(
          RedisConnection connection,
          @Nullable RedissonRequest request,
          @Nullable Context context,
          @Nullable Scope scope,
          boolean multiSpan,
          boolean suppressedSpan) {
        this.connection = connection;
        this.request = request;
        this.context = context;
        this.scope = scope;
        this.multiSpan = multiSpan;
        this.suppressedSpan = suppressedSpan;
      }

      private static AdviceScope suppressed(RedisConnection connection) {
        return new AdviceScope(connection, null, null, null, false, true);
      }

      @Nullable
      public static AdviceScope start(RedisConnection connection, Object arg) {
        InetSocketAddress remoteAddress =
            (InetSocketAddress) connection.getChannel().remoteAddress();
        RedissonRequest request = RedissonRequest.create(remoteAddress, arg);
        PromiseWrapper<?> promise = request.getPromiseWrapper();
        if (promise == null) {
          return null;
        }
        if (RedissonBatchSpanManager.suppressSpanOrEndMultiSpan(connection, request, promise)) {
          return suppressed(connection);
        }
        Context parentContext = currentContext();
        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }

        Context context = instrumenter().start(parentContext, request);
        Scope scope = context.makeCurrent();

        if (request.isMultiBatch()) {
          RedissonBatchSpanManager.startMultiSpan(connection, instrumenter(), context, request);
          return new AdviceScope(connection, request, context, scope, true, false);
        }
        promise.setEndOperationListener(
            new EndOperationListener<>(instrumenter(), context, request));
        return new AdviceScope(connection, request, context, scope, false, false);
      }

      public void end(@Nullable Throwable throwable) {
        if (scope != null) {
          scope.close();
        }

        if (throwable != null) {
          if (multiSpan || suppressedSpan) {
            RedissonBatchSpanManager.endMultiSpan(connection, throwable);
          } else if (context != null && request != null) {
            instrumenter().end(context, request, null, throwable);
          }
        }
        // span ended in EndOperationListener
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This RedisConnection connection, @Advice.Argument(0) Object arg) {
      return AdviceScope.start(connection, arg);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
