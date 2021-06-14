/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SpanInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.api.trace.PropagatedSpan");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isStatic()).and(named("create")),
        SpanInstrumentation.class.getName() + "$CreateAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateAdvice {

    // We replace the return value completely so don't need to call the method.
    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static boolean methodEnter() {
      return false;
    }

    @Advice.OnMethodExit
    public static void methodExit(
        @Advice.Argument(0) SpanContext applicationSpanContext,
        @Advice.Return(readOnly = false) Span applicationSpan) {
      applicationSpan =
          Bridging.toApplication(
              io.opentelemetry.api.trace.Span.wrap(Bridging.toAgent(applicationSpanContext)));
    }
  }
}
