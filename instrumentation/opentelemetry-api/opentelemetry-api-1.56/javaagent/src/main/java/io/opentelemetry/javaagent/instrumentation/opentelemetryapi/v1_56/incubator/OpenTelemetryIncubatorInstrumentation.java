/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.common.ApplicationComponentLoader156;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator.config.ApplicationConfigProvider156Incubator;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator.config.ApplicationDeclarativeConfigProperties156Incubator;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class OpenTelemetryIncubatorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.api.GlobalOpenTelemetry");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        none(), OpenTelemetryIncubatorInstrumentation.class.getName() + "$InitAdvice");
  }

  @SuppressWarnings({"ReturnValueIgnored", "unused"})
  public static class InitAdvice {
    @Advice.OnMethodEnter
    public static void init() {
      // the sole purpose of this advice is to ensure that the classes are
      // recognized as helper class and injected into class loader
      ApplicationOpenTelemetry156Incubator.class.getName();
      ApplicationDeclarativeConfigProperties156Incubator.class.getName();
      ApplicationConfigProvider156Incubator.class.getName();
      ApplicationComponentLoader156.class.getName();
    }
  }
}
