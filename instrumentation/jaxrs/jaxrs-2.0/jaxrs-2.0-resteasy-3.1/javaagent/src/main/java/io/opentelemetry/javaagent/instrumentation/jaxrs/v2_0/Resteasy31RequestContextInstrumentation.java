/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;
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
public class Resteasy31RequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  @Override
  protected String abortAdviceName() {
    return ContainerRequestContextAdvice.class.getName();
  }

  public static class ContainerRequestContextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void decorateAbortSpan(
        @Advice.This ContainerRequestContext requestContext,
        @Local("otelContext") Context context,
        @Local("otelScope") Scope scope) {
      if (requestContext.getProperty(JaxRsAnnotationsTracer.ABORT_HANDLED) == null
          && requestContext instanceof PostMatchContainerRequestContext) {

        ResourceMethodInvoker resourceMethodInvoker =
            ((PostMatchContainerRequestContext) requestContext).getResourceMethod();
        Method method = resourceMethodInvoker.getMethod();
        Class<?> resourceClass = resourceMethodInvoker.getResourceClass();

        context =
            RequestContextHelper.createOrUpdateAbortSpan(requestContext, resourceClass, method);
        if (context != null) {
          scope = context.makeCurrent();
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Local("otelContext") Context context,
        @Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      RequestContextHelper.closeSpanAndScope(context, scope, throwable);
    }
  }
}
