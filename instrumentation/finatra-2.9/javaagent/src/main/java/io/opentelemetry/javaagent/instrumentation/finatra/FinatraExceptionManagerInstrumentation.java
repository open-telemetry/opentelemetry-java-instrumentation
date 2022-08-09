/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.twitter.finagle.http.Response;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class FinatraExceptionManagerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.twitter.finatra.http.exceptions.ExceptionManager");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("toResponse"))
            .and(takesArgument(1, Throwable.class))
            .and(returns(named("com.twitter.finagle.http.Response"))),
        this.getClass().getName() + "$HandleExceptionAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleExceptionAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void handleException(
        @Advice.Return Response response, @Advice.Argument(1) Throwable throwable) {

      if (throwable == null) {
        return;
      }

      VirtualField<Response, Throwable> virtualField =
          VirtualField.find(Response.class, Throwable.class);
      virtualField.set(response, throwable);
    }
  }
}
