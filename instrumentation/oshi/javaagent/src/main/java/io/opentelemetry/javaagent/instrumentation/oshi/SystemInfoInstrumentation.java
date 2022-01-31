/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.oshi.ProcessMetrics;
import io.opentelemetry.instrumentation.oshi.SystemMetrics;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SystemInfoInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("oshi.SystemInfo");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("oshi.SystemInfo");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(isStatic())
            .and(named("getCurrentPlatformEnum").or(named("getCurrentPlatform"))),
        this.getClass().getName() + "$GetCurrentPlatformEnumAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetCurrentPlatformEnumAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      SystemMetrics.registerObservers();

      // ProcessMetrics don't follow the spec
      if (Config.get()
          .getBoolean("otel.instrumentation.oshi.experimental-metrics.enabled", false)) {
        ProcessMetrics.registerObservers();
      }
    }
  }
}
