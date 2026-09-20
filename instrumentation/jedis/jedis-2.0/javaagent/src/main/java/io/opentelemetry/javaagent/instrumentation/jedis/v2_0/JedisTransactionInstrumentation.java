/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisPipelineContext.transactionFraming;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.currentTransactionFraming;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
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
    if (emitStableDatabaseSemconv()) {
      transformer.applyAdviceToMethod(
          named("execGetResponse"), getClass().getName() + "$ExecAdvice");
    }
    transformer.applyAdviceToMethod(named("discard"), getClass().getName() + "$DiscardAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecAdvice {

    public static class AdviceState {
      @Nullable private final Context context;
      @Nullable private final JedisRequest request;
      @Nullable public Scope scope;
      @Nullable public TransactionFraming previousTransactionFraming;

      public AdviceState(@Nullable Context context, @Nullable JedisRequest request) {
        this.context = context;
        this.request = request;
      }

      public void end(@Nullable Throwable throwable) {
        if (scope != null) {
          scope.close();
        }
        if (context != null && request != null) {
          instrumenter().end(context, request, null, throwable);
        }
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceState onEnter(@Advice.This Object transaction) {
      BatchState batchState = JedisPipelineContext.takeBatchState(transaction);
      @Nullable JedisRequest request = null;
      @Nullable Context context = null;
      if (batchState != null && !batchState.getRequests().isEmpty()) {
        request =
            JedisRequest.createTransaction(
                batchState.getRequests(), batchState.getTransactionFramingPeerAddress());
        Context parentContext = Java8BytecodeBridge.currentContext();
        if (instrumenter().shouldStart(parentContext, request)) {
          context = instrumenter().start(parentContext, request);
        }
      }

      // Suppress the EXEC framing command's own span; the transaction is reported as a single
      // batch span here. An empty transaction has no peer to report, so it has no batch span.
      AdviceState adviceState = new AdviceState(context, request);
      adviceState.previousTransactionFraming =
          currentTransactionFraming().set(transactionFraming(request));
      if (context != null) {
        try {
          adviceState.scope = context.makeCurrent();
        } catch (RuntimeException e) {
          currentTransactionFraming().restore(adviceState.previousTransactionFraming);
          throw e;
        } catch (Error error) {
          currentTransactionFraming().restore(adviceState.previousTransactionFraming);
          throw error;
        }
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

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static TransactionFraming onEnter(@Advice.This Object transaction) {
      // A discarded transaction is abandoned, so drop its captured commands without reporting a
      // batch span, and suppress the DISCARD framing command's own span.
      JedisPipelineContext.clear(transaction);
      return currentTransactionFraming().set(transactionFraming(null));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable TransactionFraming previous) {
      currentTransactionFraming().restore(previous);
    }
  }
}
