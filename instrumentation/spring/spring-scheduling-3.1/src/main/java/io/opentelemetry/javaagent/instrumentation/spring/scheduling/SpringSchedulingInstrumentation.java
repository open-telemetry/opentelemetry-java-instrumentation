/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class SpringSchedulingInstrumentation extends Instrumenter.Default {

  public SpringSchedulingInstrumentation() {
    super("spring-scheduling");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.scheduling.config.Task");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".SpringSchedulingTracer", packageName + ".SpringSchedulingRunnableWrapper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor().and(takesArgument(0, Runnable.class)),
        SpringSchedulingInstrumentation.class.getName() + "$SpringSchedulingAdvice");
  }

  public static class SpringSchedulingAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onConstruction(
        @Advice.Argument(value = 0, readOnly = false) Runnable runnable) {
      runnable = SpringSchedulingRunnableWrapper.wrapIfNeeded(runnable);
    }
  }
}
