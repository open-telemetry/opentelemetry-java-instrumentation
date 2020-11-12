/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxRsAnnotationsTracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Local;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.interception.PostMatchContainerRequestContext;

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
final class Resteasy30RequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  @Override
  protected String abortAdviceName() {
    return ContainerRequestContextAdvice.class.getName();
  }

  public static class ContainerRequestContextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void decorateAbortSpan(
        @Advice.This ContainerRequestContext context,
        @Local("otelSpan") Span span,
        @Local("otelScope") Scope scope) {
      if (context.getProperty(JaxRsAnnotationsTracer.ABORT_HANDLED) == null
          && context instanceof PostMatchContainerRequestContext) {

        ResourceMethodInvoker resourceMethodInvoker =
            ((PostMatchContainerRequestContext) context).getResourceMethod();
        Method method = resourceMethodInvoker.getMethod();
        Class<?> resourceClass = resourceMethodInvoker.getResourceClass();

        span = RequestContextHelper.createOrUpdateAbortSpan(context, resourceClass, method);
        if (span != null) {
          scope = tracer().startScope(span);
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Local("otelSpan") Span span,
        @Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      RequestContextHelper.closeSpanAndScope(span, scope, throwable);
    }
  }
}
