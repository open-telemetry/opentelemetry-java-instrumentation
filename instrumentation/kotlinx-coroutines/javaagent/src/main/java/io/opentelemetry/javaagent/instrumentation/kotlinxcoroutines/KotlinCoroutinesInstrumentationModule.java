/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlin.coroutines.CoroutineContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class KotlinCoroutinesInstrumentationModule extends InstrumentationModule {

  public KotlinCoroutinesInstrumentationModule() {
    super("kotlinx-coroutines");
  }

  @Override
  public String[] additionalHelperClassNames() {
    return new String[] {
      "io.opentelemetry.extension.kotlin.ContextExtensionsKt",
      "io.opentelemetry.extension.kotlin.KotlinContextElement",
      "io.opentelemetry.extension.kotlin.KotlinContextElement$1"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new CoroutineScopeLaunchInstrumentation());
  }

  public static class CoroutineScopeLaunchInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return named("kotlinx.coroutines.BuildersKt");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(
          (named("launch").or(named("launch$default")))
              .and(takesArgument(1, named("kotlin.coroutines.CoroutineContext"))),
          KotlinCoroutinesInstrumentationModule.class.getName() + "$LaunchAdvice");
      transformers.put(
          (named("runBlocking").or(named("runBlocking$default")))
              .and(takesArgument(0, named("kotlin.coroutines.CoroutineContext"))),
          KotlinCoroutinesInstrumentationModule.class.getName() + "$RunBlockingAdvice");
      return transformers;
    }
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
