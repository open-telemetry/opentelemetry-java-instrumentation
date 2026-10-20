/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisBatchState;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JedisPipelineInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // appendCommand is declared on different pipeline/transaction base types across the supported
    // jedis range (the names churn between 4.x, 5.x, and 7.x); match all of them so both pipelines
    // and transactions are captured.
    return namedOneOf(
        "redis.clients.jedis.Pipeline",
        "redis.clients.jedis.PipelineBase",
        "redis.clients.jedis.Transaction",
        "redis.clients.jedis.PipelinedTransactionBase",
        "redis.clients.jedis.TransactionBase",
        "redis.clients.jedis.PipeliningBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("appendCommand"), getClass().getName() + "$QueueCommandAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("sync", "syncAndReturnAll"), getClass().getName() + "$SyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class QueueCommandAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object onEnter(@Advice.This Object pipeline) {
      // Attaches a thread-local pipeline that the nested Connection.sendCommand advice uses to
      // collect captured requests; sync() then consumes them to build the batch span.
      return JedisPipelineContext.enter(pipeline);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopCollecting(@Advice.Enter @Nullable Object previous) {
      JedisPipelineContext.exit(previous);
    }
  }

  @SuppressWarnings("unused")
  public static class SyncAdvice {

    public static class AdviceScope {
      @Nullable private final Context context;
      private final Context captureContext;
      private final Scope scope;
      private final JedisRequest request;

      private AdviceScope(
          @Nullable Context context, Context captureContext, Scope scope, JedisRequest request) {
        this.context = context;
        this.captureContext = captureContext;
        this.scope = scope;
        this.request = request;
      }

      @Nullable
      public static AdviceScope start(Object pipeline) {
        List<JedisRequest> requests = JedisPipelineContext.getAndClearCapturedRequests(pipeline);
        if (requests.isEmpty()) {
          // An empty pipeline sends nothing to the server, and with no captured request there is no
          // connection to derive server attributes from, so it is not reported as a batch span.
          return null;
        }
        JedisRequest request = JedisRequest.createPipeline(requests);
        Context parentContext = Context.current();
        @Nullable Context context = null;
        Context captureParent = parentContext;
        if (instrumenter().shouldStart(parentContext, request)) {
          context = instrumenter().start(parentContext, request);
          captureParent = context;
        }
        Context captureContext =
            JedisBatchState.store(captureParent, request.getConnection(), requests.size());
        if (context == null) {
          return new AdviceScope(null, captureContext, captureContext.makeCurrent(), request);
        }
        return new AdviceScope(
            captureContext, captureContext, captureContext.makeCurrent(), request);
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        if (context != null) {
          instrumenter()
              .end(
                  context,
                  request,
                  JedisBatchState.getErrorType(captureContext, throwable),
                  throwable);
        }
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(@Advice.This Object pipeline) {
      return AdviceScope.start(pipeline);
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
