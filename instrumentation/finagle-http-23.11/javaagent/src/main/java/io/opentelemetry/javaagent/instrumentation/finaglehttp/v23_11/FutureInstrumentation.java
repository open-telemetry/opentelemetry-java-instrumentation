/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.twitter.util.Future;
import com.twitter.util.Try;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.Function1;
import scala.runtime.BoxedUnit;

/**
 * Instruments the Promise state machine so that all chains in the Futures/Fibers are
 * otel-Context-coherent.
 */
class FutureInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.twitter.util.ConstFuture");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("respond")), getClass().getName() + "$RespondAdvice");

    // transformTry is documented as not being run in the scheduler, so it's not handled
    transformer.applyAdviceToMethod(
        isMethod().and(named("transform")), getClass().getName() + "$TransformAdvice");
  }

  @SuppressWarnings("unused")
  public static class RespondAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) Function1<Try<?>, BoxedUnit> f) {
      f = TwitterUtilCoreHelpers.wrap(Context.current(), f);
    }
  }

  @SuppressWarnings("unused")
  public static class TransformAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) Function1<Try<?>, Future<?>> f) {
      f = TwitterUtilCoreHelpers.wrap(Context.current(), f);
    }
  }
}
