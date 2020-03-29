/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.auto.instrumentation.jaxrs.v2_0.JaxRsAnnotationsDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jaxrs.v2_0.JaxRsAnnotationsDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
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
      "io.opentelemetry.auto.tooling.ClassHierarchyIterable",
      "io.opentelemetry.auto.tooling.ClassHierarchyIterable$ClassIterator",
      packageName + ".JaxRsAnnotationsDecorator",
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
    public static SpanWithScope createOrUpdateAbortSpan(
        final ContainerRequestContext context, final Class resourceClass, final Method method) {

      if (method != null && resourceClass != null) {
        context.setProperty(JaxRsAnnotationsDecorator.ABORT_HANDLED, true);
        // The ordering of the specific and general abort instrumentation is unspecified
        // The general instrumentation (ContainerRequestFilterInstrumentation) saves spans
        // properties if it ran first
        Span parent = (Span) context.getProperty(JaxRsAnnotationsDecorator.ABORT_PARENT);
        Span span = (Span) context.getProperty(JaxRsAnnotationsDecorator.ABORT_SPAN);

        if (span == null) {
          parent = TRACER.getCurrentSpan();
          span = TRACER.spanBuilder("jax-rs.request.abort").startSpan();

          final SpanWithScope scope = new SpanWithScope(span, currentContextWith(span));

          DECORATE.afterStart(span);
          DECORATE.onJaxRsSpan(span, parent, resourceClass, method);

          return scope;
        } else {
          DECORATE.onJaxRsSpan(span, parent, resourceClass, method);
          return null;
        }
      } else {
        return null;
      }
    }

    public static void closeSpanAndScope(
        final SpanWithScope spanWithScope, final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }

      final Span span = spanWithScope.getSpan();
      if (throwable != null) {
        DECORATE.onError(span, throwable);
      }

      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
    }
  }
}
