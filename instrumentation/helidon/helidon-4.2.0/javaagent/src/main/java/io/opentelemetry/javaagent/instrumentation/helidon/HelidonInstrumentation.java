/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.helidon;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.helidon.webserver.http.HttpRouting;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HelidonInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("io.helidon.webserver.http.HttpRouting"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(isStatic()).and(named("builder")),
        HelidonInstrumentation.class.getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Return HttpRouting.Builder httpContext) {
      HelidonSingletons.FILTERS.forEach(httpContext::addFeature);
    }
  }
}
