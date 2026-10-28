/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisSingletons.transactionFramingSuppression;
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

class JedisTransactionInstrumentation implements TypeInstrumentation {
  public static class TransactionState {
    @Nullable private JedisRequestContext<?> requestContext;
    private boolean acquiredTransactionFramingSuppression;

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
    // exec()/discard() are declared on different transaction base types across the jedis range.
    return namedOneOf(
        "redis.clients.jedis.Transaction",
        "redis.clients.jedis.PipelinedTransactionBase",
        "redis.clients.jedis.TransactionBase",
        "redis.clients.jedis.AbstractTransaction");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("exec"), getClass().getName() + "$ExecAdvice");
    transformer.applyAdviceToMethod(named("discard"), getClass().getName() + "$DiscardAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static TransactionState onEnter(@Advice.This Object transaction) {
      List<JedisRequest> requests = JedisPipelineContext.getAndClearCapturedRequests(transaction);
      TransactionState state = new TransactionState();
      state.acquiredTransactionFramingSuppression = transactionFramingSuppression().tryAcquire();
      try {
        if (requests.isEmpty()) {
          // EXEC still runs, but without queued requests there is no batch span to report.
          state.requestContext = JedisRequestContext.attachForTransaction(transaction);
        } else {
          JedisRequest request = JedisRequest.createTransaction(requests);
          state.requestContext =
              JedisRequestContext.startBatch(
                  instrumenter(), request, transaction, request.getConnection());
        }
        return state;
      } catch (Throwable t) {
        if (state.acquiredTransactionFramingSuppression) {
          transactionFramingSuppression().release();
        }
        state.complete(null);
        throw t;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Return @Nullable List<?> result,
        @Advice.Enter @Nullable TransactionState state) {
      if (state != null) {
        if (state.acquiredTransactionFramingSuppression) {
          transactionFramingSuppression().release();
        }
        state.complete(throwable, result);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class DiscardAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static TransactionState onEnter(@Advice.This Object transaction) {
      // A discarded transaction is abandoned, so drop its captured commands without reporting a
      // batch span, and suppress the DISCARD framing command's own span.
      JedisPipelineContext.getAndClearCapturedRequests(transaction);
      TransactionState state = new TransactionState();
      state.acquiredTransactionFramingSuppression = transactionFramingSuppression().tryAcquire();
      try {
        state.requestContext = JedisRequestContext.attachForBatch();
        return state;
      } catch (Throwable t) {
        if (state.acquiredTransactionFramingSuppression) {
          transactionFramingSuppression().release();
        }
        state.complete(null);
        throw t;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable TransactionState state) {
      if (state != null) {
        if (state.acquiredTransactionFramingSuppression) {
          transactionFramingSuppression().release();
        }
        state.complete(throwable);
      }
    }
  }
}
