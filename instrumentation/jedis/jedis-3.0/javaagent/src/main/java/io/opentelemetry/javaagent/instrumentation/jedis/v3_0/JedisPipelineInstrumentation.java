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

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisRequestContext;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
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
    public static void onEnter(@Advice.This PipelineBase pipeline) {
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
    public static JedisRequestContext<JedisRequest> onEnter(@Advice.This PipelineBase pipeline) {
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
