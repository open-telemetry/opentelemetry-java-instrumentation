/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.Function0;

/**
 * Instruments the narrow window where a caller, possibly bearing a Context, can push work onto a
 * FuturePool. While the underlying Executor/Service instruments the Runnable, there is possibility
 * for it to leak at the call site in the transition.
 */
class FuturePoolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // also covers InterruptibleExecutorServiceFuturePool (extension)
    return named("com.twitter.util.ExecutorServiceFuturePool");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("apply").and(takesArgument(0, named("scala.Function0")))),
        getClass().getName() + "$ApplyAdvice");
  }

  @SuppressWarnings("unused")
  public static class ApplyAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onApplyEnter(@Advice.Argument(value = 0, readOnly = false) Function0<?> fn) {
      if (fn != null) {
        fn = TwitterUtilCoreHelpers.wrap(Context.current(), fn);
      }
    }
  }
}
