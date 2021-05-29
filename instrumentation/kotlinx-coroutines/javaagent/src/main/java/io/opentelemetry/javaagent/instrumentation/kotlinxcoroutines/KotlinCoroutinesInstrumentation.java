/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import kotlin.coroutines.CoroutineContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class KotlinCoroutinesInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("kotlinx.coroutines.BuildersKt");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("launch")
            .or(named("launch$default"))
            .and(takesArgument(1, named("kotlin.coroutines.CoroutineContext"))),
        this.getClass().getName() + "$LaunchAdvice");
    transformer.applyAdviceToMethod(
        named("runBlocking")
            .or(named("runBlocking$default"))
            .and(takesArgument(0, named("kotlin.coroutines.CoroutineContext"))),
        this.getClass().getName() + "$RunBlockingAdvice");
  }

  public static class LaunchAdvice {
    @Advice.OnMethodEnter
    public static void enter(
        @Advice.Argument(value = 1, readOnly = false) CoroutineContext coroutineContext) {
      coroutineContext =
          KotlinCoroutinesInstrumentationHelper.addOpenTelemetryContext(coroutineContext);
    }
  }

  public static class RunBlockingAdvice {
    @Advice.OnMethodEnter
    public static void enter(
        @Advice.Argument(value = 0, readOnly = false) CoroutineContext coroutineContext) {
      coroutineContext =
          KotlinCoroutinesInstrumentationHelper.addOpenTelemetryContext(coroutineContext);
    }
  }
}
