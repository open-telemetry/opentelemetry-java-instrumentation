/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisPipelineContext.transactionFraming;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.currentTransactionFraming;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisRequestContext;
import io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisPipelineContext.BatchState;
import io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisPipelineContext.TransactionFraming;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JedisTransactionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // exec()/discard() are declared on BinaryTransaction in jedis 2.0.x and on Transaction in later
    // 2.x; match both so they are instrumented across the range.
    return namedOneOf("redis.clients.jedis.Transaction", "redis.clients.jedis.BinaryTransaction");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("exec"), getClass().getName() + "$ExecAdvice");
    transformer.applyAdviceToMethod(named("execGetResponse"), getClass().getName() + "$ExecAdvice");
    transformer.applyAdviceToMethod(named("discard"), getClass().getName() + "$DiscardAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecAdvice {

    public static class AdviceState {
      @Nullable private JedisRequestContext<JedisRequest> requestContext;
      @Nullable public TransactionFraming previousTransactionFraming;

      public void end(@Nullable Throwable throwable) {
        if (requestContext != null) {
          requestContext.detachAndEnd(throwable);
        }
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceState onEnter(@Advice.This Object transaction) {
      BatchState batchState = JedisPipelineContext.takeBatchState(transaction);
      @Nullable JedisRequest request = null;
      if (batchState != null && !batchState.getRequests().isEmpty()) {
        request =
            JedisRequest.createTransaction(
                batchState.getRequests(), batchState.getTransactionFramingPeerAddress());
      }

      // Suppress the EXEC framing command's own span; the transaction is reported as a single
      // batch span here. An empty transaction still sends EXEC but has no batch span.
      AdviceState adviceState = new AdviceState();
      adviceState.previousTransactionFraming =
          currentTransactionFraming().set(transactionFraming(request));
      if (request != null) {
        try {
          adviceState.requestContext =
              JedisRequestContext.startBatch(instrumenter(), request, transaction);
        } catch (Throwable t) {
          if (adviceState.requestContext != null) {
            adviceState.requestContext.detachAndEnd(null);
          }
          currentTransactionFraming().restore(adviceState.previousTransactionFraming);
          throw t;
        }
      } else {
        adviceState.requestContext = JedisRequestContext.attachForTransaction(transaction);
      }
      return adviceState;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceState adviceState) {
      if (adviceState == null) {
        return;
      }
      currentTransactionFraming().restore(adviceState.previousTransactionFraming);
      adviceState.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class DiscardAdvice {

    public static class DiscardState {
      private final JedisRequestContext<?> requestContext;
      @Nullable private final TransactionFraming previousTransactionFraming;

      private DiscardState(
          JedisRequestContext<?> requestContext,
          @Nullable TransactionFraming previousTransactionFraming) {
        this.requestContext = requestContext;
        this.previousTransactionFraming = previousTransactionFraming;
      }

      public void end(@Nullable Throwable throwable) {
        currentTransactionFraming().restore(previousTransactionFraming);
        requestContext.detachAndEnd(throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static DiscardState onEnter(@Advice.This Object transaction) {
      // A discarded transaction is abandoned, so drop its captured commands without reporting a
      // batch span, and suppress the DISCARD framing command's own span.
      JedisPipelineContext.clear(transaction);
      TransactionFraming previousTransactionFraming =
          currentTransactionFraming().set(transactionFraming(null));
      return new DiscardState(JedisRequestContext.attachForBatch(), previousTransactionFraming);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter DiscardState discardState) {
      discardState.end(throwable);
    }
  }
}
