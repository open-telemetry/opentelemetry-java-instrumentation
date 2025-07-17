/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.servlet.v2_2.Servlet2Singletons.RESPONSE_STATUS;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Class <code>javax.servlet.http.HttpServletResponse</code> got method <code>getStatus</code> only
 * in Servlet specification version 3.0. This means that we cannot set {@link
 * io.opentelemetry.semconv.HttpAttributes#HTTP_RESPONSE_STATUS_CODE} attribute on the created span
 * using just response object.
 *
 * <p>This instrumentation intercepts status setting methods from Servlet 2.0 specification and
 * stores that status into context store. Then {@link Servlet2Advice#stopSpan(ServletRequest,
 * ServletResponse, Throwable, Servlet2Advice.AdviceScope)} can get it from context and set required
 * span attribute.
 */
public class Servlet2HttpServletResponseInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.servlet.http.HttpServletResponse");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("javax.servlet.http.HttpServletResponse"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("sendError", "setStatus"),
        Servlet2HttpServletResponseInstrumentation.class.getName()
            + "$Servlet2ResponseStatusAdvice");
    transformer.applyAdviceToMethod(
        named("sendRedirect"),
        Servlet2HttpServletResponseInstrumentation.class.getName()
            + "$Servlet2ResponseRedirectAdvice");
  }

  @SuppressWarnings("unused")
  public static class Servlet2ResponseRedirectAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This HttpServletResponse response) {
      RESPONSE_STATUS.set(response, 302);
    }
  }

  @SuppressWarnings("unused")
  public static class Servlet2ResponseStatusAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This HttpServletResponse response, @Advice.Argument(0) Integer status) {
      RESPONSE_STATUS.set(response, status);
    }
  }
}
