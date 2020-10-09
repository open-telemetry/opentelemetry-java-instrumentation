/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import application.io.opentelemetry.trace.Span;
import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.DefaultSpan;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class DefaultSpanInstrumentation extends AbstractInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.trace.DefaultSpan");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod().and(isPublic()).and(isStatic()).and(named("create")),
        DefaultSpanInstrumentation.class.getName() + "$CreateAdvice");
  }

  public static class CreateAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Return(readOnly = false) Span applicationSpan) {
      applicationSpan =
          Bridging.toApplication(
              DefaultSpan.create(Bridging.toAgent(applicationSpan.getContext())));
    }
  }
}
