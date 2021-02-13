/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.dispatcher;

import static io.opentelemetry.instrumentation.api.tracer.HttpServerTracer.CONTEXT_ATTRIBUTE;
import static io.opentelemetry.javaagent.instrumentation.servlet.dispatcher.RequestDispatcherTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RequestDispatcherInstrumentationModule extends InstrumentationModule {
  public RequestDispatcherInstrumentationModule() {
    super("servlet", "servlet-dispatcher");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RequestDispatcherInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("javax.servlet.RequestDispatcher", String.class.getName());
  }

  public static class RequestDispatcherInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("javax.servlet.RequestDispatcher");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return implementsInterface(named("javax.servlet.RequestDispatcher"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          namedOneOf("forward", "include")
              .and(takesArguments(2))
              .and(takesArgument(0, named("javax.servlet.ServletRequest")))
              .and(takesArgument(1, named("javax.servlet.ServletResponse")))
              .and(isPublic()),
          RequestDispatcherInstrumentationModule.class.getName() + "$RequestDispatcherAdvice");
    }
  }

  public static class RequestDispatcherAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void start(
        @Advice.Origin Method method,
        @Advice.This RequestDispatcher dispatcher,
        @Advice.Local("_originalContext") Object originalContext,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Argument(0) ServletRequest request) {

      Context parentContext = Java8BytecodeBridge.currentContext();

      Object servletContextObject = request.getAttribute(CONTEXT_ATTRIBUTE);
      Context servletContext =
          servletContextObject instanceof Context ? (Context) servletContextObject : null;

      Span parentSpan = Java8BytecodeBridge.spanFromContext(parentContext);
      SpanContext parentSpanContext = parentSpan.getSpanContext();
      if (!parentSpanContext.isValid() && servletContext == null) {
        // Don't want to generate a new top-level span
        return;
      }

      Span servletSpan =
          servletContext != null ? Java8BytecodeBridge.spanFromContext(servletContext) : null;
      Context parent;
      if (servletContext == null
          || (parentSpanContext.isValid()
              && servletSpan
                  .getSpanContext()
                  .getTraceId()
                  .equals(parentSpanContext.getTraceId()))) {
        // Use the parentSpan if the servletSpan is null or part of the same trace.
        parent = parentContext;
      } else {
        // parentSpan is part of a different trace, so lets ignore it.
        // This can happen with the way Tomcat does error handling.
        parent = servletContext;
      }

      try (Scope ignored = parent.makeCurrent()) {
        context = tracer().startSpan(method);

        // save the original servlet span before overwriting the request attribute, so that it can
        // be
        // restored on method exit
        originalContext = request.getAttribute(CONTEXT_ATTRIBUTE);

        // this tells the dispatched servlet to use the current span as the parent for its work
        request.setAttribute(CONTEXT_ATTRIBUTE, context);
      }
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stop(
        @Advice.Local("_originalContext") Object originalContext,
        @Advice.Argument(0) ServletRequest request,
        @Advice.Local("otelContext") Context context,
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
        tracer().endExceptionally(context, throwable);
      } else {
        tracer().end(context);
      }
    }
  }
}
