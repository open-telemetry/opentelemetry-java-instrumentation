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
      WebAsyncManagerContext.setViaRequestContextHolder(currentContext);
    }
  }

  // Helper class to store and retrieve context for async processing
  public static class WebAsyncManagerContext {
    public static final String CONTEXT_ATTRIBUTE =
        "io.opentelemetry.javaagent.spring.webmvc.async.Context";

    private WebAsyncManagerContext() {}

    static void setViaRequestContextHolder(Context context) {
      // Use Spring's RequestContextHolder to access the current request and store the context
      try {
        Class<?> requestContextHolderClass =
            Class.forName("org.springframework.web.context.request.RequestContextHolder");
        Object requestAttributes =
            requestContextHolderClass.getMethod("getRequestAttributes").invoke(null);
        if (requestAttributes != null) {
          requestAttributes
              .getClass()
              .getMethod("setAttribute", String.class, Object.class, int.class)
              .invoke(requestAttributes, CONTEXT_ATTRIBUTE, context, 0); // 0 = REQUEST_SCOPE
        }
      } catch (Exception e) {
        // Ignore - best effort
      }
    }

    @Nullable
    public static Context getFromRequest(Object request) {
      try {
        return (Context)
            request
                .getClass()
                .getMethod("getAttribute", String.class)
                .invoke(request, CONTEXT_ATTRIBUTE);
      } catch (Exception e) {
        // Ignore - best effort
        return null;
      }
    }
  }
}
