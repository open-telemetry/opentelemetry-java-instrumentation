/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket.v8_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class DefaultExceptionMapperInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.wicket.DefaultExceptionMapper");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("mapUnexpectedExceptions").and(takesArgument(0, named(Exception.class.getName()))),
        getClass().getName() + "$ExceptionAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExceptionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(0) Exception exception) {
      WicketExceptionRecorder.record(Context.current(), exception);
    }
  }
}
