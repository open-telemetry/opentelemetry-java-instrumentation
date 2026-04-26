/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.twitter.util.Promise;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instruments the Promise state machine so that all chains in the Futures/Fibers are
 * otel-Context-coherent.
 */
class PromiseKInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // includes Transformer's two lone derivatives
    return named("com.twitter.util.Promise$Transformer")
        .or(named("com.twitter.util.Promise$Monitored"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("com.twitter.util.Local$Context"))),
        getClass().getName() + "$TrapContextAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("apply").and(takesArgument(0, named("com.twitter.util.Try")))),
        getClass().getName() + "$ApplyAdvice");
  }

  @SuppressWarnings("unused")
  public static class TrapContextAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Promise.K thiz) {
      Context current = Context.current();
      TwitterUtilCoreHelpers.PROMISE_K_CONTEXT_FIELD.set(thiz, current);
    }
  }

  @SuppressWarnings("unused")
  public static class ApplyAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Scope onApplyEnter(@Advice.This Promise.K thiz) {
      // if this is null, there's a bug in the instrumentation
      Context savedContext = TwitterUtilCoreHelpers.PROMISE_K_CONTEXT_FIELD.get(thiz);
      return savedContext != null ? savedContext.makeCurrent() : null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onApplyExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
