/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// Our convention for accessing agent package
@SuppressWarnings("UnnecessarilyFullyQualified")
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
            .and(named("get"))
            .and(takesArguments(0))
            .and(returns(named("application.io.opentelemetry.api.OpenTelemetry"))),
        OpenTelemetryInstrumentation.class.getName() + "$GetAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isStatic())
            .and(named("set"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("application.io.opentelemetry.api.OpenTelemetry"))),
        OpenTelemetryInstrumentation.class.getName() + "$SetAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return(readOnly = false)
            application.io.opentelemetry.api.OpenTelemetry openTelemetry) {
      openTelemetry = ApplicationOpenTelemetry.INSTANCE;
    }
  }

  @SuppressWarnings("unused")
  public static class SetAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      Logger.getLogger(application.io.opentelemetry.api.GlobalOpenTelemetry.class.getName())
          .log(
              Level.WARNING,
              "You are currently using the OpenTelemetry Instrumentation Java Agent;"
                  + " all GlobalOpenTelemetry.set calls are ignored - the agent provides"
                  + " the global OpenTelemetry object used by your application.",
              new Throwable());
    }
  }
}
