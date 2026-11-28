/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.boot.actuator.autoconfigure.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.fieldType;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.MicrometerSingletons;
import java.util.Iterator;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class NestedMeterRegistryCloserInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
            "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration$MeterRegistryCloser",
            "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration$MeterRegistryCloser")
        .and(declaresField(named("meterRegistries").and(fieldType(Iterable.class))));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(
                takesArguments(
                    named("org.springframework.context.ApplicationContext"), is(boolean.class))),
        getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.FieldValue("meterRegistries") Iterable<MeterRegistry> registries) {
      Iterator<MeterRegistry> iterator = registries.iterator();
      while (iterator.hasNext()) {
        if (iterator.next() == MicrometerSingletons.meterRegistry()) {
          iterator.remove();
        }
      }
    }
  }
}
