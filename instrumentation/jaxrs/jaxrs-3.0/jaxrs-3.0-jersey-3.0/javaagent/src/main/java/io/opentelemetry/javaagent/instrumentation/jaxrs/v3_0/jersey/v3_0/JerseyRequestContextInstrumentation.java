/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.jersey.v3_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.jersey.v3_0.JerseySingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsConstants;
import io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.AbstractRequestContextInstrumentation;
import io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.Jaxrs3HandlerData;
import io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.Jaxrs3RequestContextHelper;
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
class JerseyRequestContextInstrumentation extends AbstractRequestContextInstrumentation {
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

      private AdviceScope(Jaxrs3HandlerData handlerData, Context context) {
        this.handlerData = handlerData;
        this.context = context;
        scope = context.makeCurrent();
      }

      @Nullable
      public static AdviceScope start(
          Class<?> resourceClass, Method method, ContainerRequestContext requestContext) {
        Jaxrs3HandlerData handlerData = new Jaxrs3HandlerData(resourceClass, method);
        Context context =
            Jaxrs3RequestContextHelper.createOrUpdateAbortSpan(
                instrumenter(), requestContext, handlerData);
        if (context == null) {
          return null;
        }
        return new AdviceScope(handlerData, context);
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        instrumenter().end(context, handlerData, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
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

      return AdviceScope.start(resourceClass, method, requestContext);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
