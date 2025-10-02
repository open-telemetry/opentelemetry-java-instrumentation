/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.exec.internal.Continuation;
import ratpack.func.Action;

public class DefaultExecutionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("ratpack.exec.internal.DefaultExecution");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        nameStartsWith("delimit") // include delimitStream
            .and(takesArgument(0, named("ratpack.func.Action")))
            .and(takesArgument(1, named("ratpack.func.Action"))),
        DefaultExecutionInstrumentation.class.getName() + "$DelimitAdvice");
  }

  @SuppressWarnings("unused")
  public static class DelimitAdvice {

    @AssignReturned.ToArguments({
      @ToArgument(value = 0, index = 0),
      @ToArgument(value = 1, index = 1)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] wrap(
        @Advice.Argument(0) Action<? super Throwable> originalOnError,
        @Advice.Argument(1) Action<? super Continuation> originalSegment) {

      Action<? super Throwable> onError = ActionWrapper.wrapIfNeeded(originalOnError);
      Action<? super Continuation> segment = ActionWrapper.wrapIfNeeded(originalSegment);
      return new Object[] {onError, segment};
    }
  }
}
