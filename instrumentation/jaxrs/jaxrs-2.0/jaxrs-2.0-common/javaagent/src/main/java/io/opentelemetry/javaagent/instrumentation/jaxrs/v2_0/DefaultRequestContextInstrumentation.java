/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxRsAnnotationsTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;
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
    return ContainerRequestContextAdvice.class.getName();
  }

  public static class ContainerRequestContextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void createGenericSpan(
        @Advice.This ContainerRequestContext requestContext,
        @Local("otelContext") Context context,
        @Local("otelScope") Scope scope) {
      if (requestContext.getProperty(JaxRsAnnotationsTracer.ABORT_HANDLED) == null) {
        Class<?> filterClass =
            (Class<?>) requestContext.getProperty(JaxRsAnnotationsTracer.ABORT_FILTER_CLASS);
        Method method = null;
        try {
          method = filterClass.getMethod("filter", ContainerRequestContext.class);
        } catch (NoSuchMethodException e) {
          // Unable to find the filter method.  This should not be reachable because the context
          // can only be aborted inside the filter method
        }

        context = tracer().startSpan(filterClass, method);
        scope = context.makeCurrent();
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
