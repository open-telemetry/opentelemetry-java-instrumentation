/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public class DemoServlet3Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return AgentElementMatchers.hasSuperType(
        namedOneOf("javax.servlet.Filter", "javax.servlet.http.HttpServlet"));
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        namedOneOf("doFilter", "service")
            .and(
                ElementMatchers.takesArgument(
                    0, ElementMatchers.named("javax.servlet.ServletRequest")))
            .and(
                ElementMatchers.takesArgument(
                    1, ElementMatchers.named("javax.servlet.ServletResponse")))
            .and(ElementMatchers.isPublic()),
        this.getClass().getName() + "$DemoServlet3Advice");
  }

  @SuppressWarnings("unused")
  public static class DemoServlet3Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 1) ServletResponse response) {
      if (!(response instanceof HttpServletResponse)) {
        return;
      }

      HttpServletResponse httpServletResponse = (HttpServletResponse) response;
      if (!httpServletResponse.containsHeader("X-server-id")) {
        httpServletResponse.setHeader(
            "X-server-id", Java8BytecodeBridge.currentSpan().getSpanContext().getTraceId());
      }
    }
  }
}
