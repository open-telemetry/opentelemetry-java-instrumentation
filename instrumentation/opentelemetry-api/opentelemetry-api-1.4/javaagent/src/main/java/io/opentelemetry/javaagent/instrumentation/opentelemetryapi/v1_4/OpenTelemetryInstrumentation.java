/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_4;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_4.trace.ApplicationTracerProvider14;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class OpenTelemetryInstrumentation implements TypeInstrumentation {

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
    @SuppressWarnings("ReturnValueIgnored")
    @Advice.OnMethodEnter(inline = false)
    public static void init() {
      // the sole purpose of this advice is to ensure that ApplicationTracerProvider14 is recognized
      // as helper class and injected into class loader
      ApplicationTracerProvider14.class.getName();
    }
  }
}
