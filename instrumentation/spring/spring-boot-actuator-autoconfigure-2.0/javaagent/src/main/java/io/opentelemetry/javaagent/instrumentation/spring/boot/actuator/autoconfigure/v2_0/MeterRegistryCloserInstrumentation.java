/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.boot.actuator.autoconfigure.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.MicrometerSingletons;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class MeterRegistryCloserInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCloser");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("trackAddedToGlobalRegistry")
            .and(takesArguments(named("io.micrometer.core.instrument.MeterRegistry"))),
        getClass().getName() + "$TrackAddedAdvice");
  }

  @SuppressWarnings("unused")
  public static class TrackAddedAdvice {

    @Advice.OnMethodEnter(
        skipOn = Advice.OnNonDefaultValue.class,
        suppress = Throwable.class,
        inline = false)
    public static boolean onEnter(@Advice.Argument(0) MeterRegistry registry) {
      return registry == MicrometerSingletons.meterRegistry();
    }
  }
}
