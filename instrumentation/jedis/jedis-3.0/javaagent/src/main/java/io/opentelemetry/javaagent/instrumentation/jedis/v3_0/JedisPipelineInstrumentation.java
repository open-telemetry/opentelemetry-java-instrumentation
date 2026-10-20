/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v3_0.JedisSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.common.v1_4.JedisBatchState;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.PipelineBase;

class JedisPipelineInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "redis.clients.jedis.Pipeline",
        "redis.clients.jedis.PipelineBase",
        "redis.clients.jedis.MultiKeyPipelineBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Queued pipeline command methods return Response.
    transformer.applyAdviceToMethod(
        isPublic().and(isMethod()).and(returns(named("redis.clients.jedis.Response"))),
        getClass().getName() + "$QueueCommandAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("sync", "syncAndReturnAll"), getClass().getName() + "$SyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class QueueCommandAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static PipelineBase onEnter(@Advice.This PipelineBase pipeline) {
      // Attaches a thread-local pipeline that the nested Connection.sendCommand advice uses to
      // collect captured requests; sync() then consumes them to build the batch span.
      return JedisPipelineContext.enter(pipeline);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopCollecting(@Advice.Enter @Nullable PipelineBase previous) {
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
      public static AdviceScope start(PipelineBase pipeline) {
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
    public static AdviceScope onEnter(@Advice.This PipelineBase pipeline) {
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
