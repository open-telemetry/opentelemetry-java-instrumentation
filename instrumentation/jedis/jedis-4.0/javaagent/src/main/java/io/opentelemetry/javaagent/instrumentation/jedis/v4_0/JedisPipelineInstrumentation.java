/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisRequestContext;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
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
    public static void onEnter(@Advice.This Object pipeline) {
      // Attaches a thread-local pipeline that the nested Connection.sendCommand advice uses to
      // collect captured requests; sync() then consumes them to build the batch span.
      JedisPipelineContext.enter(pipeline);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopCollecting() {
      JedisPipelineContext.exit();
    }
  }

  @SuppressWarnings("unused")
  public static class SyncAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static JedisRequestContext<JedisRequest> onEnter(@Advice.This Object pipeline) {
      List<JedisRequest> requests = JedisPipelineContext.getAndClearCapturedRequests(pipeline);
      if (requests.isEmpty()) {
        // An empty pipeline sends nothing to the server, and with no captured request there is no
        // connection to derive server attributes from, so it is not reported as a batch span.
        return null;
      }
      JedisRequest request = JedisRequest.createPipeline(requests);
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
