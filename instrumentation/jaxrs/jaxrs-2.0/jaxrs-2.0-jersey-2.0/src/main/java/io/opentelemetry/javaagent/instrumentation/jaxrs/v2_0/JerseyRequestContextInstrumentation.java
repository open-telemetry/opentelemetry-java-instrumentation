/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxRsAnnotationsTracer.TRACER;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.UriInfo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Local;

/**
 * Jersey specific context instrumentation.
 *
 * <p>JAX-RS does not define a way to get the matched resource method from the <code>
 * ContainerRequestContext</code>
 *
 * <p>In the Jersey implementation, <code>UriInfo</code> implements <code>ResourceInfo</code>. The
 * matched resource method can be retrieved from that object
 */
@AutoService(Instrumenter.class)
public class JerseyRequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  public static class ContainerRequestContextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void decorateAbortSpan(
        @Advice.This ContainerRequestContext context,
        @Local("otelSpan") Span span,
        @Local("otelScope") Scope scope) {
      UriInfo uriInfo = context.getUriInfo();

      if (context.getProperty(JaxRsAnnotationsTracer.ABORT_HANDLED) == null
          && uriInfo instanceof ResourceInfo) {

        ResourceInfo resourceInfo = (ResourceInfo) uriInfo;
        Method method = resourceInfo.getResourceMethod();
        Class<?> resourceClass = resourceInfo.getResourceClass();

        span = RequestFilterHelper.createOrUpdateAbortSpan(context, resourceClass, method);
        if (span != null) {
          scope = TRACER.startScope(span);
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Local("otelSpan") Span span,
        @Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      RequestFilterHelper.closeSpanAndScope(span, scope, throwable);
    }
  }
}
