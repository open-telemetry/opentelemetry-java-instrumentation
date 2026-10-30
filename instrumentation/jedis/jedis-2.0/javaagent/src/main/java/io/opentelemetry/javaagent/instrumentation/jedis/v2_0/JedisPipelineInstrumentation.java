/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.currentBatch;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisRequestContext;
import io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisPipelineContext.BatchState;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Queable;
import redis.clients.jedis.Transaction;

class JedisPipelineInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // Transaction/BinaryTransaction declare the queued command methods directly in jedis 2.0.x
    // (where they do not yet extend MultiKeyPipelineBase).
    return namedOneOf(
        "redis.clients.jedis.Pipeline",
        "redis.clients.jedis.PipelineBase",
        "redis.clients.jedis.MultiKeyPipelineBase",
        "redis.clients.jedis.Transaction",
        "redis.clients.jedis.BinaryTransaction");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic().and(isMethod()).and(returns(named("redis.clients.jedis.Response"))),
        getClass().getName() + "$QueueCommandAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("sync", "syncAndReturnAll"), getClass().getName() + "$SyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class QueueCommandAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Queable onEnter(@Advice.This Object pipeline) {
      // Attaches a thread-local pipeline that the nested Connection.sendCommand advice uses to
      // collect captured requests; sync() then consumes them to build the batch span.
      Queable previous = currentBatch().get();
      // Other Queable subtypes have no flush point, so leaving them uncaptured keeps their
      // per-command spans.
      if (pipeline instanceof Pipeline || pipeline instanceof Transaction) {
        return currentBatch().set((Queable) pipeline);
      }
      return previous;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopCollecting(@Advice.Enter @Nullable Queable previous) {
      currentBatch().restore(previous);
    }
  }

  @SuppressWarnings("unused")
  public static class SyncAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static JedisRequestContext<JedisRequest> onEnter(@Advice.This Object pipeline) {
      BatchState batchState = JedisPipelineContext.takeBatchState(pipeline);
      if (batchState == null || batchState.getRequests().isEmpty()) {
        // An empty pipeline sends nothing to the server, and with no captured request there is no
        // connection to derive server attributes from, so it is not reported as a batch span.
        return null;
      }
      JedisRequest request = JedisRequest.createPipeline(batchState.getRequests());
      return JedisRequestContext.startBatch(instrumenter(), request, null, request.getConnection());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) @Nullable Object result,
        @Advice.Enter @Nullable JedisRequestContext<JedisRequest> requestContext) {
      if (requestContext != null) {
        requestContext.detachAndEnd(throwable, result);
      }
    }
  }
}
