/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_55;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_55.incubator.ApplicationOpenTelemetry155Incubator;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_55.incubator.config.ApplicationConfigProvider155Incubator;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_55.incubator.config.ApplicationDeclarativeConfigProperties155Incubator;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class OpenTelemetryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.api.GlobalOpenTelemetry");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        none(), OpenTelemetryInstrumentation.class.getName() + "$InitAdvice");
  }

  @SuppressWarnings({"ReturnValueIgnored", "unused"})
  public static class InitAdvice {
    @Advice.OnMethodEnter
    public static void init() {
      // the sole purpose of this advice is to ensure that the classes are
      // recognized as helper class and injected into class loader
      ApplicationOpenTelemetry155Incubator.class.getName();
      ApplicationDeclarativeConfigProperties155Incubator.class.getName();
      ApplicationConfigProvider155Incubator.class.getName();
    }
  }
}
