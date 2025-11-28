/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import jakarta.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instruments DeferredResult.setResult() to capture the context at the moment the result is set.
 * This is necessary because Spring's AsyncContext.dispatch() may be called asynchronously after the
 * span that set the result has already ended, leading to incorrect context propagation.
 *
 * <p>The captured context is stored directly in the ServletRequest attribute, overwriting whatever
 * context was there before. This ensures that when AsyncContext.dispatch() is called and
 * AsyncDispatchAdvice runs, it will use the correct context (the one that was active when
 * setResult() was called, which includes any async work spans).
 */
public class DeferredResultInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.web.context.request.async.DeferredResult");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.web.context.request.async.DeferredResult");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("setResult"))
            .and(takesArguments(1))
            .and(takesArgument(0, Object.class)),
        DeferredResultInstrumentation.class.getName() + "$SetResultAdvice");
  }

  @SuppressWarnings("unused")
  public static class SetResultAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      // Capture the current context at the moment setResult() is called.
      // This context includes any spans that are currently active (e.g., async work spans).
      Context currentContext = Java8BytecodeBridge.currentContext();

      // Try to get the current HttpServletRequest from Spring's RequestContextHolder
      // and update its context attribute. This ensures that when AsyncContext.dispatch()
      // is called (possibly after this method returns), AsyncDispatchAdvice will use
      // the correct context.
      try {
        Class<?> requestContextHolderClass =
            Class.forName(
                "org.springframework.web.context.request.RequestContextHolder", false, null);
        Object requestAttributes =
            requestContextHolderClass.getMethod("getRequestAttributes").invoke(null);

        if (requestAttributes != null) {
          Class<?> servletRequestAttributesClass =
              Class.forName(
                  "org.springframework.web.context.request.ServletRequestAttributes", false, null);
          if (servletRequestAttributesClass.isInstance(requestAttributes)) {
            HttpServletRequest request =
                (HttpServletRequest)
                    servletRequestAttributesClass.getMethod("getRequest").invoke(requestAttributes);
            if (request != null) {
              // Update the servlet request attribute with the current context
              request.setAttribute(
                  "io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper.Context",
                  currentContext);
            }
          }
        }
      } catch (Exception ignored) {
        // If we can't get the request, silently fail. The existing instrumentation
        // will still work, just without the improved async context propagation.
      }
    }
  }
}
