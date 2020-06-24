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
package io.opentelemetry.auto.instrumentation.servlet.dispatcher;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerTracer.CONTEXT_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.servlet.dispatcher.RequestDispatcherDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.servlet.dispatcher.RequestDispatcherDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.auto.tooling.matcher.NameMatchers.namedOneOf;
import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.TracingContextUtils.getSpan;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
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
      packageName + ".RequestDispatcherDecorator",
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
    public static SpanWithScope start(
        @Advice.Origin("#m") final String method,
        @Advice.This final RequestDispatcher dispatcher,
        @Advice.Local("_originalServletContext") Object originalServletContext,
        @Advice.Argument(0) final ServletRequest request) {
      final Span parentSpan = TRACER.getCurrentSpan();

      final Object servletContextObject = request.getAttribute(CONTEXT_ATTRIBUTE);
      final Span servletSpan =
          servletContextObject instanceof Context ? getSpan((Context) servletContextObject) : null;

      if (!parentSpan.getContext().isValid() && servletSpan == null) {
        // Don't want to generate a new top-level span
        return null;
      }
      final Span parent;
      if (servletSpan == null
          || (parentSpan.getContext().isValid()
              && servletSpan
                  .getContext()
                  .getTraceId()
                  .equals(parentSpan.getContext().getTraceId()))) {
        // Use the parentSpan if the servletSpan is null or part of the same trace.
        parent = parentSpan;
      } else {
        // parentSpan is part of a different trace, so lets ignore it.
        // This can happen with the way Tomcat does error handling.
        parent = servletSpan;
      }

      final String target =
          InstrumentationContext.get(RequestDispatcher.class, String.class).get(dispatcher);
      final Span span =
          TRACER
              .spanBuilder("servlet." + method)
              .setParent(parent)
              .setAttribute("dispatcher.target", target)
              .startSpan();
      DECORATE.afterStart(span);

      // save the original servlet span before overwriting the request attribute, so that it can be
      // restored on method exit
      originalServletContext = request.getAttribute(CONTEXT_ATTRIBUTE);

      // this tells the dispatched servlet to use the current span as the parent for its work
      Context newContext = withSpan(span, Context.current());
      request.setAttribute(CONTEXT_ATTRIBUTE, newContext);

      return new SpanWithScope(span, withScopedContext(newContext));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stop(
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Local("_originalServletContext") final Object originalServletContext,
        @Advice.Argument(0) final ServletRequest request,
        @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }

      // restore the original servlet span
      // since spanWithScope is non-null here, originalServletContext must have been set with the
      // prior
      // servlet span (as opposed to remaining unset)
      // TODO review this logic. Seems like manual context management
      request.setAttribute(CONTEXT_ATTRIBUTE, originalServletContext);

      final Span span = spanWithScope.getSpan();
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);

      span.end();
      spanWithScope.closeScope();
    }
  }
}
