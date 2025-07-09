/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.JerseySingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsConstants;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;

/**
 * Jersey specific context instrumentation.
 *
 * <p>JAX-RS does not define a way to get the matched resource method from the <code>
 * ContainerRequestContext</code>
 *
 * <p>In the Jersey implementation, <code>UriInfo</code> implements <code>ResourceInfo</code>. The
 * matched resource method can be retrieved from that object
 */
public class JerseyRequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  @Override
  protected String abortAdviceName() {
    return getClass().getName() + "$ContainerRequestContextAdvice";
  }

  @SuppressWarnings("unused")
  public static class ContainerRequestContextAdvice {

    public static class AdviceScope {
      private final Jaxrs3HandlerData handlerData;
      private final Context context;
      private final Scope scope;

      public AdviceScope(
          Class<?> resourceClass, Method method, ContainerRequestContext requestContext) {
        handlerData = new Jaxrs3HandlerData(resourceClass, method);
        context =
            Jaxrs3RequestContextHelper.createOrUpdateAbortSpan(
                instrumenter(), requestContext, handlerData);
        scope = context != null ? context.makeCurrent() : null;
      }

      public void exit(@Nullable Throwable throwable) {
        if (scope == null) {
          return;
        }
        scope.close();
        instrumenter().end(context, handlerData, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope decorateAbortSpan(
        @Advice.This ContainerRequestContext requestContext) {

      UriInfo uriInfo = requestContext.getUriInfo();
      if (requestContext.getProperty(JaxrsConstants.ABORT_HANDLED) != null
          || !(uriInfo instanceof ResourceInfo)) {
        return null;
      }

      ResourceInfo resourceInfo = (ResourceInfo) uriInfo;
      Method method = resourceInfo.getResourceMethod();
      Class<?> resourceClass = resourceInfo.getResourceClass();

      if (resourceClass == null || method == null) {
        return null;
      }

      return new AdviceScope(resourceClass, method, requestContext);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable, @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.exit(throwable);
      }
    }
  }
}
