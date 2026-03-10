/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxrsAnnotationsSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsConstants;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsServerSpanNaming;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.asm.Advice;

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

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;
      private final Jaxrs2HandlerData handlerData;

      private AdviceScope(Context context, Scope scope, Jaxrs2HandlerData handlerData) {
        this.context = context;
        this.scope = scope;
        this.handlerData = handlerData;
      }

      @Nullable
      public static AdviceScope enter(Class<?> filterClass, Method method) {

        Context parentContext = Context.current();
        Jaxrs2HandlerData handlerData = new Jaxrs2HandlerData(filterClass, method);

        HttpServerRoute.update(
            parentContext,
            HttpServerRouteSource.CONTROLLER,
            JaxrsServerSpanNaming.SERVER_SPAN_NAME,
            handlerData);

        if (!instrumenter().shouldStart(parentContext, handlerData)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, handlerData);
        return new AdviceScope(context, context.makeCurrent(), handlerData);
      }

      public void exit(Throwable throwable) {
        if (scope == null) {
          return;
        }
        scope.close();
        instrumenter().end(context, handlerData, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope createGenericSpan(
        @Advice.This ContainerRequestContext requestContext) {

      if (requestContext.getProperty(JaxrsConstants.ABORT_HANDLED) != null) {
        return null;
      }

      Class<?> filterClass =
          (Class<?>) requestContext.getProperty(JaxrsConstants.ABORT_FILTER_CLASS);
      if (filterClass == null) {
        return null;
      }

      Method method = null;
      try {
        method = filterClass.getMethod("filter", ContainerRequestContext.class);
      } catch (NoSuchMethodException e) {
        // Unable to find the filter method.  This should not be reachable because the context
        // can only be aborted inside the filter method
      }

      if (method == null) {
        return null;
      }
      return AdviceScope.enter(filterClass, method);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {

      if (adviceScope != null) {
        adviceScope.exit(throwable);
      }
    }
  }
}
