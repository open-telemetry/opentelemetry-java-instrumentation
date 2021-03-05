/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.wicket.WicketRuntimeException;

public class DefaultExceptionMapperInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.apache.wicket.DefaultExceptionMapper");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        named("mapUnexpectedExceptions").and(takesArgument(0, named(Exception.class.getName()))),
        DefaultExceptionMapperInstrumentation.class.getName() + "$ExceptionAdvice");
  }

  public static class ExceptionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onExit(@Advice.Argument(0) Exception exception) {
      Span serverSpan = ServerSpan.fromContextOrNull(Java8BytecodeBridge.currentContext());
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
