/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import application.io.opentelemetry.context.Context;
import com.google.auto.service.AutoService;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// TODO: Actually bridge correlation context. We currently just stub out withBaggage
// to have minimum functionality with SDK shim implementations.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/973
@AutoService(Instrumenter.class)
public class BaggageUtilsInstrumentation extends AbstractInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.api.baggage.BaggageUtils");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(isPublic()).and(isStatic()).and(named("withBaggage")).and(takesArguments(2)),
        BaggageUtilsInstrumentation.class.getName() + "$WithBaggageAdvice");
    return transformers;
  }

  public static class WithBaggageAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) Baggage applicationBaggage,
        @Advice.Argument(1) Context applicationContext,
        @Advice.Return(readOnly = false) Context applicationUpdatedContext) {
      applicationUpdatedContext = applicationContext;
    }
  }
}
