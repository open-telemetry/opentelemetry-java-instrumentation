/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SystemInfoInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("oshi.SystemInfo");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Instrument the no-arg constructor: prior versions exposed static
    // getCurrentPlatformEnum()/getCurrentPlatform() entry points, but oshi 7.0.0 removed both, so
    // we trigger registration on instantiation instead (works across all supported versions).
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(0)),
        getClass().getName() + "$ConstructAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit() {
      MetricsRegistration.register();
    }
  }
}
