/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import jakarta.servlet.http.HttpServletRequest;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class WebAsyncManagerInstrumentation implements TypeInstrumentation {

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
    // Instrument setResult to capture the context when the async result is set
    // This is called from async code (e.g., in TestBean.asyncCall()) and we need to
    // capture the context that includes the async work span so it can be used for the redispatch
    transformer.applyAdviceToMethod(
        isMethod().and(named("setResult")).and(takesArguments(1)),
        WebAsyncManagerInstrumentation.class.getName() + "$SetResultAdvice");
  }

  @SuppressWarnings("unused")
  public static class SetResultAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      // Capture the current context when DeferredResult.setResult() is called
      // This is the context that includes the async work (e.g., the async-call-child span)
      // and should be used when the handler is redispatched
      Context currentContext = Java8BytecodeBridge.currentContext();

      // Store context in request attributes via Spring's RequestContextHolder
      try {
        Class<?> requestContextHolderClass =
            Class.forName("org.springframework.web.context.request.RequestContextHolder");
        Object requestAttributes =
            requestContextHolderClass.getMethod("getRequestAttributes").invoke(null);
        if (requestAttributes != null) {
          requestAttributes
              .getClass()
              .getMethod("setAttribute", String.class, Object.class, int.class)
              .invoke(
                  requestAttributes,
                  "io.opentelemetry.javaagent.spring.webmvc.async.Context",
                  currentContext,
                  0); // 0 = REQUEST_SCOPE
        }
      } catch (Exception e) {
        // Ignore - best effort
      }
    }
  }

  // Helper method to retrieve the stored async context from the request
  @Nullable
  public static Context getAsyncContext(HttpServletRequest request) {
    try {
      return (Context)
          request
              .getClass()
              .getMethod("getAttribute", String.class)
              .invoke(request, "io.opentelemetry.javaagent.spring.webmvc.async.Context");
    } catch (Exception e) {
      // Ignore - best effort
      return null;
    }
  }
}
