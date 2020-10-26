/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.http;

import static io.opentelemetry.javaagent.instrumentation.servlet.http.HttpServletResponseTracer.TRACER;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap.Depth;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class HttpServletResponseInstrumentation extends Instrumenter.Default {
  public HttpServletResponseInstrumentation() {
    super("servlet", "servlet-response");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("javax.servlet.http.HttpServletResponse");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.servlet.http.HttpServletResponse"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".HttpServletResponseTracer",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(namedOneOf("sendError", "sendRedirect"), SendAdvice.class.getName());
  }

  public static class SendAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void start(
        @Advice.Origin Method method,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") Depth callDepth) {
      callDepth = CallDepthThreadLocalMap.getCallDepth(HttpServletResponse.class);
      // Don't want to generate a new top-level span
      if (callDepth.getAndIncrement() == 0
          && Java8BytecodeBridge.currentSpan().getSpanContext().isValid()) {
        span = TRACER.startSpan(method);
        scope = span.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") Depth callDepth) {
      if (callDepth.decrementAndGet() == 0 && span != null) {
        CallDepthThreadLocalMap.reset(HttpServletResponse.class);

        scope.close();

        if (throwable != null) {
          TRACER.endExceptionally(span, throwable);
        } else {
          TRACER.end(span);
        }
      }
    }
  }
}
