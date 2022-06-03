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
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Local;
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

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void decorateAbortSpan(
        @Advice.This AbstractRequestContextImpl requestContext,
        @Local("otelHandlerData") Jaxrs2HandlerData handlerData,
        @Local("otelContext") Context context,
        @Local("otelScope") Scope scope) {

      if (requestContext.getProperty(JaxrsConstants.ABORT_HANDLED) != null
          || !(requestContext instanceof ContainerRequestContext)) {
        return;
      }

      Message message = requestContext.getMessage();
      OperationResourceInfoStack resourceInfoStack =
          (OperationResourceInfoStack)
              message.get("org.apache.cxf.jaxrs.model.OperationResourceInfoStack");
      if (resourceInfoStack == null || resourceInfoStack.isEmpty()) {
        return;
      }

      MethodInvocationInfo invocationInfo = resourceInfoStack.peek();
      Method method = invocationInfo.getMethodInfo().getMethodToInvoke();
      Class<?> resourceClass = invocationInfo.getRealClass();

      handlerData = new Jaxrs2HandlerData(resourceClass, method);
      context =
          Jaxrs2RequestContextHelper.createOrUpdateAbortSpan(
              instrumenter(), (ContainerRequestContext) requestContext, handlerData);
      if (context != null) {
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Local("otelHandlerData") Jaxrs2HandlerData handlerData,
        @Local("otelContext") Context context,
        @Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      scope.close();
      instrumenter().end(context, handlerData, null, throwable);
    }
  }
}
