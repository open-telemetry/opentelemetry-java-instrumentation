/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxRsAnnotationsTracer.TRACER;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.lang.reflect.Method;
import java.util.Map;
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public abstract class AbstractRequestContextInstrumentation extends Instrumenter.Default {
  public AbstractRequestContextInstrumentation() {
    super("jax-rs", "jaxrs", "jax-rs-filter");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("javax.ws.rs.container.ContainerRequestContext");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.ws.rs.container.ContainerRequestContext"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.javaagent.tooling.ClassHierarchyIterable",
      "io.opentelemetry.javaagent.tooling.ClassHierarchyIterable$ClassIterator",
      packageName + ".JaxRsAnnotationsTracer",
      AbstractRequestContextInstrumentation.class.getName() + "$RequestFilterHelper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("abortWith"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("javax.ws.rs.core.Response"))),
        getClass().getName() + "$ContainerRequestContextAdvice");
  }

  public static class RequestFilterHelper {
    public static Span createOrUpdateAbortSpan(
        ContainerRequestContext requestContext, Class<?> resourceClass, Method method) {

      if (method != null && resourceClass != null) {
        requestContext.setProperty(JaxRsAnnotationsTracer.ABORT_HANDLED, true);
        Context context = Java8BytecodeBridge.currentContext();
        Span serverSpan = BaseTracer.getCurrentServerSpan(context);
        Span currentSpan = Java8BytecodeBridge.spanFromContext(context);

        // if there's no current span or it's the same as the server (servlet) span we need to start
        // a JAX-RS one
        // in other case, DefaultRequestContextInstrumentation must have already run so it's enough
        // to just update the names
        if (currentSpan == null || currentSpan == serverSpan) {
          return TRACER.startSpan(resourceClass, method);
        } else {
          TRACER.updateSpanNames(context, currentSpan, serverSpan, resourceClass, method);
        }
      }
      return null;
    }

    public static void closeSpanAndScope(Span span, Scope scope, Throwable throwable) {
      if (span == null || scope == null) {
        return;
      }

      if (throwable != null) {
        TRACER.endExceptionally(span, throwable);
      } else {
        TRACER.end(span);
      }

      scope.close();
    }
  }
}
