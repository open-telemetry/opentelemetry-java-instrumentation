/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
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
    transformer.applyAdviceToMethod(
        namedOneOf("exec", "execGetResponse"), getClass().getName() + "$ExecAdvice");
    transformer.applyAdviceToMethod(named("discard"), getClass().getName() + "$DiscardAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecAdvice {

    public static class AdviceScope {
      @Nullable private final Context context;
      @Nullable private final Scope scope;
      @Nullable private final JedisRequest request;
      @Nullable private final Object previousTransactionFraming;

      private AdviceScope(
          @Nullable Context context,
          @Nullable Scope scope,
          @Nullable JedisRequest request,
          @Nullable Object previousTransactionFraming) {
        this.context = context;
        this.scope = scope;
        this.request = request;
        this.previousTransactionFraming = previousTransactionFraming;
      }

      public static AdviceScope start(Object transaction) {
        List<JedisRequest> requests = JedisPipelineContext.getAndClearCapturedRequests(transaction);
        JedisRequest multiRequest =
            JedisPipelineContext.getAndClearTransactionFramingRequest(transaction);
        // Suppress the EXEC framing command's own span; the transaction is reported as a single
        // batch span here.
        if (requests.isEmpty()) {
          // An empty transaction sends nothing for the batch, and with no captured request there
          // is no connection to derive server attributes from, so it is not reported as a batch
          // span.
          Object previous = JedisPipelineContext.enterTransactionFraming();
          return new AdviceScope(null, null, null, previous);
        }
        JedisRequest request = JedisRequest.createTransaction(requests, multiRequest);
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, request)) {
          Object previous = JedisPipelineContext.enterTransactionFraming(request);
          return new AdviceScope(null, null, null, previous);
        }
        Context context = instrumenter().start(parentContext, request);
        Scope scope = context.makeCurrent();
        Object previous = JedisPipelineContext.enterTransactionFraming(request);
        return new AdviceScope(context, scope, request, previous);
      }

      public void end(@Nullable Throwable throwable) {
        JedisPipelineContext.exitTransactionFraming(previousTransactionFraming);
        if (scope != null) {
          scope.close();
          instrumenter().end(context, request, null, throwable);
        }
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(@Advice.This Object transaction) {
      return AdviceScope.start(transaction);
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

  @SuppressWarnings("unused")
  public static class DiscardAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnter(@Advice.This Object transaction) {
      // A discarded transaction is abandoned, so drop its captured commands without reporting a
      // batch span, and suppress the DISCARD framing command's own span.
      JedisPipelineContext.clear(transaction);
      return JedisPipelineContext.enterTransactionFraming();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Object previous) {
      JedisPipelineContext.exitTransactionFraming(previous);
    }
  }
}
