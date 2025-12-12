/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_57;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class OpenTelemetryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.api.GlobalOpenTelemetry");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isStatic())
            .and(named("isSet"))
            .and(takesArguments(0))
            .and(returns(boolean.class)),
        OpenTelemetryInstrumentation.class.getName() + "$IsSetAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isStatic())
            .and(named("getOrNoop"))
            .and(takesArguments(0))
            .and(returns(named("application.io.opentelemetry.api.OpenTelemetry"))),
        OpenTelemetryInstrumentation.class.getName() + "$GetOrNoopAdvice");
  }

  @SuppressWarnings("unused")
  public static class IsSetAdvice {
    @AssignReturned.ToReturned
    @Advice.OnMethodExit
    public static boolean methodExit() {
      return true;
    }
  }

  @SuppressWarnings("unused")
  public static class GetOrNoopAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit
    public static application.io.opentelemetry.api.OpenTelemetry methodExit() {
      return application.io.opentelemetry.api.GlobalOpenTelemetry.get();
    }
  }
}
