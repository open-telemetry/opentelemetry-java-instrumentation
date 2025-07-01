/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.CxfSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsConstants;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.cxf.jaxrs.impl.AbstractRequestContextImpl;
import org.apache.cxf.jaxrs.model.MethodInvocationInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfoStack;
import org.apache.cxf.message.Message;

/**
 * CXF specific context instrumentation.
 *
 * <p>JAX-RS does not define a way to get the matched resource method from the <code>
 * ContainerRequestContext</code>
 *
 * <p>In the CXF implementation, <code>Message</code> contains <code>OperationResourceInfoStack
 * </code> which contains <code>MethodInvocationInfo</code>. The matched resource method can be
 * retrieved from that object
 */
public class CxfRequestContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.cxf.jaxrs.impl.AbstractRequestContextImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("abortWith"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("javax.ws.rs.core.Response"))),
        CxfRequestContextInstrumentation.class.getName() + "$ContainerRequestContextAdvice");
  }

  @SuppressWarnings("unused")
  public static class ContainerRequestContextAdvice {

    public static class AdviceScope {
      private final Jaxrs2HandlerData handlerData;
      private final Context context;
      private final Scope scope;

      public AdviceScope(
          Class<?> resourceClass, Method method, AbstractRequestContextImpl requestContext) {
        handlerData = new Jaxrs2HandlerData(resourceClass, method);
        context =
            Jaxrs2RequestContextHelper.createOrUpdateAbortSpan(
                instrumenter(), (ContainerRequestContext) requestContext, handlerData);
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
        @Advice.This AbstractRequestContextImpl requestContext) {

      if (requestContext.getProperty(JaxrsConstants.ABORT_HANDLED) != null
          || !(requestContext instanceof ContainerRequestContext)) {
        return null;
      }

      Message message = requestContext.getMessage();
      OperationResourceInfoStack resourceInfoStack =
          (OperationResourceInfoStack)
              message.get("org.apache.cxf.jaxrs.model.OperationResourceInfoStack");
      if (resourceInfoStack == null || resourceInfoStack.isEmpty()) {
        return null;
      }

      MethodInvocationInfo invocationInfo = resourceInfoStack.peek();
      Method method = invocationInfo.getMethodInfo().getMethodToInvoke();
      Class<?> resourceClass = invocationInfo.getRealClass();
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
