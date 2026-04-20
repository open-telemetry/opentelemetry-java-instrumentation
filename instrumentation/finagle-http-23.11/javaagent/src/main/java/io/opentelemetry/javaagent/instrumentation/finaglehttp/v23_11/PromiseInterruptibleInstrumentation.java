/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

/**
 * Inspired by Kamon's approach, instruments the interruptible such that it has access to the
 * Context active on the Promise.
 */
class PromiseInterruptibleInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.twitter.util.Promise$Interruptible");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToArguments(@ToArgument(1))
    public static PartialFunction<Throwable, BoxedUnit> onEnter(
        @Advice.Argument(1) PartialFunction<Throwable, BoxedUnit> handler) {
      if (handler instanceof TwitterUtilCoreHelpers.InterruptibleWithContext) {
        return handler;
      }
      Context context = Context.current();
      if (context == Context.root()) {
        return handler;
      }
      return new TwitterUtilCoreHelpers.InterruptibleWithContext(context, handler);
    }
  }
}
