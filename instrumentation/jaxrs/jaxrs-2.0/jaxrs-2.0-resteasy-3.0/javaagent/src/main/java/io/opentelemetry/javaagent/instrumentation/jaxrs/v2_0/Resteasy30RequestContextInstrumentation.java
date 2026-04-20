/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.Resteasy30Singletons.instrumenter;

import io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.common.AbstractRequestContextInstrumentation;
import io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.common.Jaxrs2HandlerData;
import io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.common.Jaxrs2RequestContextHelper;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsConstants;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.asm.Advice;
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
public class Resteasy30RequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  @Override
  protected String abortAdviceName() {
    return getClass().getName() + "$ContainerRequestContextAdvice";
  }

  @SuppressWarnings("unused")
  public static class ContainerRequestContextAdvice {

    public static class AdviceScope {
      private final Jaxrs2HandlerData handlerData;
      private final Context context;
      private final Scope scope;

      private AdviceScope(Jaxrs2HandlerData handlerData, Context context) {
        this.handlerData = handlerData;
        this.context = context;
        scope = context.makeCurrent();
      }

      @Nullable
      public static AdviceScope start(
          Class<?> resourceClass, Method method, ContainerRequestContext requestContext) {
        Jaxrs2HandlerData handlerData = new Jaxrs2HandlerData(resourceClass, method);
        Context context =
            Jaxrs2RequestContextHelper.createOrUpdateAbortSpan(
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
