/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.JaxrsAnnotationsSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsConstants;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsServerSpanNaming;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Local;

/**
 * Default context instrumentation.
 *
 * <p>JAX-RS does not define a way to get the matched resource method from the <code>
 * ContainerRequestContext</code>
 *
 * <p>This default instrumentation uses the class name of the filter to create the span. More
 * specific instrumentations may override this value.
 */
public class DefaultRequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  @Override
  protected String abortAdviceName() {
    return getClass().getName() + "$ContainerRequestContextAdvice";
  }

  @SuppressWarnings("unused")
  public static class ContainerRequestContextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void createGenericSpan(
        @Advice.This ContainerRequestContext requestContext,
        @Local("otelHandlerData") Jaxrs3HandlerData handlerData,
        @Local("otelContext") Context context,
        @Local("otelScope") Scope scope) {
      if (requestContext.getProperty(JaxrsConstants.ABORT_HANDLED) != null) {
        return;
      }

      Class<?> filterClass =
          (Class<?>) requestContext.getProperty(JaxrsConstants.ABORT_FILTER_CLASS);
      Method method = null;
      try {
        method = filterClass.getMethod("filter", ContainerRequestContext.class);
      } catch (NoSuchMethodException e) {
        // Unable to find the filter method.  This should not be reachable because the context
        // can only be aborted inside the filter method
      }

      if (filterClass == null || method == null) {
        return;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      handlerData = new Jaxrs3HandlerData(filterClass, method);

      HttpRouteHolder.updateHttpRoute(
          parentContext,
          HttpRouteSource.CONTROLLER,
          JaxrsServerSpanNaming.SERVER_SPAN_NAME,
          handlerData);

      if (!instrumenter().shouldStart(parentContext, handlerData)) {
        return;
      }

      context = instrumenter().start(parentContext, handlerData);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Local("otelHandlerData") Jaxrs3HandlerData handlerData,
        @Local("otelContext") Context context,
        @Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }

      scope.close();
      instrumenter().end(context, handlerData, null, throwable);
    }
  }
}
