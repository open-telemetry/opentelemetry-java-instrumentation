/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.InvocationTargetException;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.wicket.WicketRuntimeException;

public class DefaultExceptionMapperInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.wicket.DefaultExceptionMapper");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("mapUnexpectedExceptions").and(takesArgument(0, named(Exception.class.getName()))),
        DefaultExceptionMapperInstrumentation.class.getName() + "$ExceptionAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExceptionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onExit(@Advice.Argument(0) Exception exception) {
      Span serverSpan = LocalRootSpan.fromContextOrNull(Java8BytecodeBridge.currentContext());
      if (serverSpan != null) {
        // unwrap exception
        Throwable throwable = exception;
        while (throwable.getCause() != null
            && (throwable instanceof WicketRuntimeException
                || throwable instanceof InvocationTargetException)) {
          throwable = throwable.getCause();
        }
        // as we don't create a span for wicket we record exception on server span
        serverSpan.recordException(throwable);
      }
    }
  }
}
