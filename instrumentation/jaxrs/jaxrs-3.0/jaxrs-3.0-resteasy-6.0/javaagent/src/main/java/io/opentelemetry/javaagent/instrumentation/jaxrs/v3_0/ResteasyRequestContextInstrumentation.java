/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.ResteasySingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsConstants;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
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

      if (requestContext.getProperty(JaxrsConstants.ABORT_HANDLED) != null
          || !(requestContext instanceof PostMatchContainerRequestContext)) {
        return null;
      }

      ResourceMethodInvoker resourceMethodInvoker =
          ((PostMatchContainerRequestContext) requestContext).getResourceMethod();
      Method method = resourceMethodInvoker.getMethod();
      Class<?> resourceClass = resourceMethodInvoker.getResourceClass();

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
