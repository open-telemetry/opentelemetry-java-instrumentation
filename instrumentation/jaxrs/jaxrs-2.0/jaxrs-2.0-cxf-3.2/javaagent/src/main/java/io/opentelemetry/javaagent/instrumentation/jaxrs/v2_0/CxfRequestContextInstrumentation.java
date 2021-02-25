/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.lang.reflect.Method;
import java.util.Map;
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Local;
import net.bytebuddy.description.method.MethodDescription;
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
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("abortWith"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("javax.ws.rs.core.Response"))),
        CxfRequestContextInstrumentation.class.getName() + "$ContainerRequestContextAdvice");
  }

  public static class ContainerRequestContextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void decorateAbortSpan(
        @Advice.This AbstractRequestContextImpl requestContext,
        @Local("otelContext") Context context,
        @Local("otelScope") Scope scope) {

      if (requestContext.getProperty(JaxRsAnnotationsTracer.ABORT_HANDLED) == null
          && requestContext instanceof ContainerRequestContext) {
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

        context =
            RequestContextHelper.createOrUpdateAbortSpan(
                (ContainerRequestContext) requestContext, resourceClass, method);
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
