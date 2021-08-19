/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.linecorp.armeria.client.WebClientBuilder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ArmeriaWebClientBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.linecorp.armeria.client.WebClientBuilder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("build")),
        ArmeriaWebClientBuilderInstrumentation.class.getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {

    @Advice.OnMethodEnter
    public static void build(@Advice.This WebClientBuilder builder) {
      builder.decorator(ArmeriaSingletons.CLIENT_DECORATOR);
    }
  }
}
