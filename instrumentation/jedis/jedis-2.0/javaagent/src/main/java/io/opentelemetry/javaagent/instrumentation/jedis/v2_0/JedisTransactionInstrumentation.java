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
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JedisTransactionInstrumentation implements TypeInstrumentation {
  public static class TransactionState {
    @Nullable private JedisRequestContext<?> requestContext;
    @Nullable private TransactionFraming previousTransactionFraming;

    public void complete(@Nullable Throwable throwable) {
      complete(throwable, null);
    }

    public void complete(@Nullable Throwable throwable, @Nullable Object result) {
      if (requestContext != null) {
        requestContext.detachAndEnd(throwable, result);
      }
    }
  }

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

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static TransactionState onEnter(@Advice.This Object transaction) {
      BatchState batchState = JedisPipelineContext.takeBatchState(transaction);
      @Nullable JedisRequest request = null;
      if (batchState != null && !batchState.getRequests().isEmpty()) {
        request =
            JedisRequest.createTransaction(
                batchState.getRequests(), batchState.getTransactionFramingPeerAddress());
      }

      // Suppress the EXEC framing command's own span; the transaction is reported as a single
      // batch span here. An empty transaction still sends EXEC but has no batch span.
      TransactionState state = new TransactionState();
      state.previousTransactionFraming =
          currentTransactionFraming().set(transactionFraming(request));
      if (request != null) {
        try {
          state.requestContext =
              JedisRequestContext.startBatch(
                  instrumenter(), request, transaction, request.getConnection());
        } catch (Throwable t) {
          currentTransactionFraming().restore(state.previousTransactionFraming);
          if (state.requestContext != null) {
            state.requestContext.detachAndEnd(null);
          }
          throw t;
        }
      } else {
        state.requestContext = JedisRequestContext.attachForTransaction(transaction);
      }
      return state;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Return @Nullable List<?> result,
        @Advice.Enter @Nullable TransactionState state) {
      if (state == null) {
        return;
      }
      currentTransactionFraming().restore(state.previousTransactionFraming);
      state.complete(throwable, result);
    }
  }

  @SuppressWarnings("unused")
  public static class DiscardAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static TransactionState onEnter(@Advice.This Object transaction) {
      // A discarded transaction is abandoned, so drop its captured commands without reporting a
      // batch span, and suppress the DISCARD framing command's own span.
      JedisPipelineContext.clear(transaction);
      TransactionState state = new TransactionState();
      state.previousTransactionFraming = currentTransactionFraming().set(transactionFraming(null));
      try {
        state.requestContext = JedisRequestContext.attachForBatch();
      } catch (Throwable t) {
        currentTransactionFraming().restore(state.previousTransactionFraming);
        state.complete(null);
        throw t;
      }
      return state;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable TransactionState state) {
      if (state != null) {
        currentTransactionFraming().restore(state.previousTransactionFraming);
        state.complete(throwable);
      }
    }
  }
}
