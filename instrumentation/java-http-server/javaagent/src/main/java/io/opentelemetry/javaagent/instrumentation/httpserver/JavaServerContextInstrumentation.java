/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpserver;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.sun.net.httpserver.HttpContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JavaServerContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("com.sun.net.httpserver.HttpServer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("createContext")),
        JavaServerContextInstrumentation.class.getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {

    @Advice.OnMethodExit
    public static void onExit(@Advice.Return HttpContext ctx) {
      ctx.getFilters().addAll(JavaSingletons.FILTERS);
    }
  }
}
