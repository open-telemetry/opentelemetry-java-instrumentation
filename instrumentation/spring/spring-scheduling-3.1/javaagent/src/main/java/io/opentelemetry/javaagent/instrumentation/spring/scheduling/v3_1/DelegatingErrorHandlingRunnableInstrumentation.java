/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.util.ErrorHandler;

public class DelegatingErrorHandlingRunnableInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.scheduling.support.DelegatingErrorHandlingRunnable");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(1, named("org.springframework.util.ErrorHandler"))),
        this.getClass().getName() + "$WrapErrorHandlerAdvice");

    transformer.applyAdviceToMethod(
        isPublic().and(named("run")), this.getClass().getName() + "$RunAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapErrorHandlerAdvice {

    @AssignReturned.ToArguments(@ToArgument(1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ErrorHandler onEnter(@Advice.Argument(1) ErrorHandler originalErrorHandler) {
      ErrorHandler errorHandler = originalErrorHandler;
      if (errorHandler != null) {
        errorHandler = new ErrorHandlerWrapper(errorHandler);
      }
      return errorHandler;
    }
  }

  @SuppressWarnings("unused")
  public static class RunAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter() {
      Context parentContext = Java8BytecodeBridge.currentContext();
      return TaskContextHolder.init(parentContext).makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
