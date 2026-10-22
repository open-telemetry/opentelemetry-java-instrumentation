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
      private final boolean previousTransactionFraming;

      private AdviceState(
          @Nullable JedisRequestContext<JedisRequest> requestContext,
          boolean previousTransactionFraming) {
        this.requestContext = requestContext;
        this.previousTransactionFraming = previousTransactionFraming;
      }

      public static AdviceState start(Transaction transaction) {
        List<JedisRequest> requests = JedisPipelineContext.getAndClearCapturedRequests(transaction);
        boolean previousTransactionFraming = JedisPipelineContext.inTransactionFraming();
        // Suppress the EXEC framing command's own span; the transaction is reported as a single
        // batch span here.
        JedisPipelineContext.enterTransactionFraming();
        if (requests.isEmpty()) {
          // EXEC still runs, but without queued requests there is no batch span to report.
          return new AdviceState(
              JedisRequestContext.attachForTransaction(transaction), previousTransactionFraming);
        }
        JedisRequest request = JedisRequest.createTransaction(requests);
        return new AdviceState(
            JedisRequestContext.startBatch(instrumenter(), request, transaction),
            previousTransactionFraming);
      }

      public void end(@Nullable Throwable throwable) {
        if (previousTransactionFraming) {
          JedisPipelineContext.enterTransactionFraming();
        } else {
          JedisPipelineContext.exitTransactionFraming();
        }
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

    public static class DiscardState {
      private final JedisRequestContext<?> requestContext;
      private final boolean previousTransactionFraming;

      private DiscardState(
          JedisRequestContext<?> requestContext, boolean previousTransactionFraming) {
        this.requestContext = requestContext;
        this.previousTransactionFraming = previousTransactionFraming;
      }

      public void end(@Nullable Throwable throwable) {
        if (previousTransactionFraming) {
          JedisPipelineContext.enterTransactionFraming();
        } else {
          JedisPipelineContext.exitTransactionFraming();
        }
        requestContext.detachAndEnd(throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static DiscardState onEnter(@Advice.This Transaction transaction) {
      // A discarded transaction is abandoned, so drop its captured commands without reporting a
      // batch span, and suppress the DISCARD framing command's own span.
      JedisPipelineContext.getAndClearCapturedRequests(transaction);
      boolean previousTransactionFraming = JedisPipelineContext.inTransactionFraming();
      JedisPipelineContext.enterTransactionFraming();
      return new DiscardState(JedisRequestContext.attachForBatch(), previousTransactionFraming);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter DiscardState discardState) {
      discardState.end(throwable);
    }
  }
}
