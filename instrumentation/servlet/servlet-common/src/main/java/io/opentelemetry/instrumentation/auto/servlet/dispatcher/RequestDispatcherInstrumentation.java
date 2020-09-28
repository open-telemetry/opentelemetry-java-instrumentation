/*
 * Copyright The OpenTelemetry Authors
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

package io.opentelemetry.instrumentation.auto.servlet.dispatcher;

import static io.opentelemetry.instrumentation.api.tracer.HttpServerTracer.CONTEXT_ATTRIBUTE;
import static io.opentelemetry.instrumentation.auto.servlet.dispatcher.RequestDispatcherTracer.TRACER;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.matcher.NameMatchers.namedOneOf;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static io.opentelemetry.trace.TracingContextUtils.getSpan;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.auto.servlet.http.HttpServletResponseTracer;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import io.opentelemetry.trace.SpanContext;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class RequestDispatcherInstrumentation extends Instrumenter.Default {
  public RequestDispatcherInstrumentation() {
    super("servlet", "servlet-dispatcher");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("javax.servlet.RequestDispatcher");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.servlet.RequestDispatcher"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".RequestDispatcherTracer",
    };
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("javax.servlet.RequestDispatcher", String.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        namedOneOf("forward", "include")
            .and(takesArguments(2))
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
            .and(isPublic()),
        getClass().getName() + "$RequestDispatcherAdvice");
  }

  public static class RequestDispatcherAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void start(
        @Advice.Origin Method method,
        @Advice.This RequestDispatcher dispatcher,
        @Advice.Local("_originalContext") Object originalContext,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Argument(0) ServletRequest request) {

      Context parentContext = Context.current();

      Object servletContextObject = request.getAttribute(CONTEXT_ATTRIBUTE);
      Context servletContext =
          servletContextObject instanceof Context ? (Context) servletContextObject : null;

      Span parentSpan = getSpan(parentContext);
      SpanContext parentSpanContext = parentSpan.getContext();
      if (!parentSpanContext.isValid() && servletContext == null) {
        // Don't want to generate a new top-level span
        return;
      }

      Span servletSpan = servletContext != null ? getSpan(servletContext) : null;
      Context parent;
      if (servletContext == null
          || (parentSpanContext.isValid()
              && servletSpan
                  .getContext()
                  .getTraceIdAsHexString()
                  .equals(parentSpanContext.getTraceIdAsHexString()))) {
        // Use the parentSpan if the servletSpan is null or part of the same trace.
        parent = parentContext;
      } else {
        // parentSpan is part of a different trace, so lets ignore it.
        // This can happen with the way Tomcat does error handling.
        parent = servletContext;
      }

      try (Scope ignored = currentContextWith(parent)) {
        span = TRACER.startSpan(method);

        // save the original servlet span before overwriting the request attribute, so that it can
        // be
        // restored on method exit
        originalContext = request.getAttribute(CONTEXT_ATTRIBUTE);

        // this tells the dispatched servlet to use the current span as the parent for its work
        Context newContext = withSpan(span, Context.current());
        request.setAttribute(CONTEXT_ATTRIBUTE, newContext);
      }
      scope = TRACER.startScope(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stop(
        @Advice.Local("_originalContext") Object originalContext,
        @Advice.Argument(0) ServletRequest request,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }

      scope.close();

      // restore the original servlet span
      // since spanWithScope is non-null here, originalContext must have been set with the
      // prior
      // servlet span (as opposed to remaining unset)
      // TODO review this logic. Seems like manual context management
      request.setAttribute(CONTEXT_ATTRIBUTE, originalContext);

      if (throwable != null) {
        HttpServletResponseTracer.TRACER.endExceptionally(span, throwable);
      } else {
        HttpServletResponseTracer.TRACER.end(span);
      }
    }
  }
}
