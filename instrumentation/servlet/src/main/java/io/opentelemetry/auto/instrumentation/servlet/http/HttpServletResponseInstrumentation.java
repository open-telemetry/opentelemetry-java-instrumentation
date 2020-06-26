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

package io.opentelemetry.auto.instrumentation.servlet.http;

import static io.opentelemetry.auto.instrumentation.servlet.http.HttpServletResponseDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.servlet.http.HttpServletResponseDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.auto.tooling.matcher.NameMatchers.namedOneOf;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
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
      packageName + ".HttpServletResponseDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(namedOneOf("sendError", "sendRedirect"), SendAdvice.class.getName());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(
        "javax.servlet.http.HttpServletResponse", "javax.servlet.http.HttpServletRequest");
  }

  public static class SendAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope start(
        @Advice.Origin("#m") final String method, @Advice.This final HttpServletResponse resp) {
      if (!TRACER.getCurrentSpan().getContext().isValid()) {
        // Don't want to generate a new top-level span
        return null;
      }

      final HttpServletRequest req =
          InstrumentationContext.get(HttpServletResponse.class, HttpServletRequest.class).get(resp);
      if (req == null) {
        // Missing the response->request linking... probably in a wrapped instance.
        return null;
      }

      final Span span = TRACER.spanBuilder("HttpServletResponse." + method).startSpan();
      DECORATE.afterStart(span);

      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      final Span span = spanWithScope.getSpan();
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
    }
  }
}
