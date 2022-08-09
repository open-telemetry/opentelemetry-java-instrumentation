/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsConstants;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Local;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.interception.jaxrs.PostMatchContainerRequestContext;

/**
 * RESTEasy specific context instrumentation.
 *
 * <p>JAX-RS does not define a way to get the matched resource method from the <code>
 * ContainerRequestContext</code>
 *
 * <p>In the RESTEasy implementation, <code>ContainerRequestContext</code> is implemented by <code>
 * PostMatchContainerRequestContext</code>. This class provides a way to get the matched resource
 * method through <code>getResourceMethod()</code>.
 */
public class ResteasyRequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  @Override
  protected String abortAdviceName() {
    return getClass().getName() + "$ContainerRequestContextAdvice";
  }

  @SuppressWarnings("unused")
  public static class ContainerRequestContextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void decorateAbortSpan(
        @Advice.This ContainerRequestContext requestContext,
        @Local("otelHandlerData") Jaxrs3HandlerData handlerData,
        @Local("otelContext") Context context,
        @Local("otelScope") Scope scope) {
      if (requestContext.getProperty(JaxrsConstants.ABORT_HANDLED) != null
          || !(requestContext instanceof PostMatchContainerRequestContext)) {
        return;
      }

      ResourceMethodInvoker resourceMethodInvoker =
          ((PostMatchContainerRequestContext) requestContext).getResourceMethod();
      Method method = resourceMethodInvoker.getMethod();
      Class<?> resourceClass = resourceMethodInvoker.getResourceClass();

      handlerData = new Jaxrs3HandlerData(resourceClass, method);
      context =
          Jaxrs3RequestContextHelper.createOrUpdateAbortSpan(
              ResteasySingletons.instrumenter(), requestContext, handlerData);
      if (context != null) {
        scope = context.makeCurrent();
      }
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
      ResteasySingletons.instrumenter().end(context, handlerData, null, throwable);
    }
  }
}
