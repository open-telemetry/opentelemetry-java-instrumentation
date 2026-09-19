/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_47.incubator.trace.ApplicationTracerFactory147Incubator;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_63.incubator.logs.ApplicationLoggerFactory163Incubator;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics.ApplicationMeterFactory165Incubator;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class OpenTelemetryIncubatorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.api.GlobalOpenTelemetry");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(none(), getClass().getName() + "$InitAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitAdvice {
    @Advice.OnMethodEnter(inline = false)
    @SuppressWarnings("ReturnValueIgnored")
    public static void init() {
      // the sole purpose of this advice is to ensure that ApplicationMeterFactory165Incubator is
      // recognized as helper class and injected into class loader
      ApplicationMeterFactory165Incubator.class.getName();
      // 1.63 instrumentation does not apply on 1.65, we include only the logs part here
      ApplicationLoggerFactory163Incubator.class.getName();
      // 1.47 instrumentation does not apply on 1.65, we include only the trace part here
      ApplicationTracerFactory147Incubator.class.getName();
    }
  }
}
