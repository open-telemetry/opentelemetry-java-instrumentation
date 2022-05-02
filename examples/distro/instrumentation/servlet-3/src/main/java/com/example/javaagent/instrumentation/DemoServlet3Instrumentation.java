/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.instrumentation;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DemoServlet3Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(namedOneOf("javax.servlet.Filter", "javax.servlet.http.HttpServlet"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("doFilter", "service")
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
            .and(isPublic()),
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
