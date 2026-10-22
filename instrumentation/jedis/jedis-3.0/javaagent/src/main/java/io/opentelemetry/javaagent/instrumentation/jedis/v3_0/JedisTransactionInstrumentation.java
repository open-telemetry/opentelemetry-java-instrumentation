/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v3_0.JedisSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisRequestContext;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.Transaction;

class JedisTransactionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.Transaction");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("exec", "execGetResponse"), getClass().getName() + "$ExecAdvice");
    transformer.applyAdviceToMethod(named("discard"), getClass().getName() + "$DiscardAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecAdvice {

    public static class AdviceState {
      @Nullable private final JedisRequestContext<JedisRequest> requestContext;

      private AdviceState(@Nullable JedisRequestContext<JedisRequest> requestContext) {
        this.requestContext = requestContext;
      }

      public static AdviceState start(Transaction transaction) {
        List<JedisRequest> requests = JedisPipelineContext.getAndClearCapturedRequests(transaction);
        // Suppress the EXEC framing command's own span; the transaction is reported as a single
        // batch span here.
        JedisPipelineContext.enterTransactionFraming();
        if (requests.isEmpty()) {
          // An empty transaction sends nothing for the batch, and with no captured request there
          // is no connection to derive server attributes from, so it is not reported as a batch
          // span.
          return new AdviceState(null);
        }
        JedisRequest request = JedisRequest.createTransaction(requests);
        return new AdviceState(JedisRequestContext.startBatch(instrumenter(), request));
      }

      public void end(@Nullable Throwable throwable) {
        JedisPipelineContext.exitTransactionFraming();
        if (requestContext != null) {
          requestContext.detachAndEnd(throwable);
        }
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceState onEnter(@Advice.This Transaction transaction) {
      return AdviceState.start(transaction);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceState adviceState) {
      adviceState.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class DiscardAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.This Transaction transaction) {
      // A discarded transaction is abandoned, so drop its captured commands without reporting a
      // batch span, and suppress the DISCARD framing command's own span.
      JedisPipelineContext.getAndClearCapturedRequests(transaction);
      JedisPipelineContext.enterTransactionFraming();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit() {
      JedisPipelineContext.exitTransactionFraming();
    }
  }
}
