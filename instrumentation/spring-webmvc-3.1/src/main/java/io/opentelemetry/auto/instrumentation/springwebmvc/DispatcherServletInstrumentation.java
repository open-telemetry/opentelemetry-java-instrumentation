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
package io.opentelemetry.auto.instrumentation.springwebmvc;

import static io.opentelemetry.auto.instrumentation.springwebmvc.SpringWebHttpServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.springwebmvc.SpringWebHttpServerDecorator.DECORATE_RENDER;
import static io.opentelemetry.auto.instrumentation.springwebmvc.SpringWebHttpServerDecorator.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;

@AutoService(Instrumenter.class)
public final class DispatcherServletInstrumentation extends Instrumenter.Default {

  public DispatcherServletInstrumentation() {
    super("spring-web");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.web.servlet.DispatcherServlet");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".SpringWebHttpServerDecorator",
      packageName + ".SpringWebHttpServerDecorator$1",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isProtected())
            .and(named("render"))
            .and(takesArgument(0, named("org.springframework.web.servlet.ModelAndView"))),
        DispatcherServletInstrumentation.class.getName() + "$DispatcherAdvice");
    transformers.put(
        isMethod()
            .and(isProtected())
            .and(nameStartsWith("processHandlerException"))
            .and(takesArgument(3, Exception.class)),
        DispatcherServletInstrumentation.class.getName() + "$ErrorHandlerAdvice");
    return transformers;
  }

  public static class DispatcherAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(@Advice.Argument(0) final ModelAndView mv) {
      final Span span = TRACER.spanBuilder(DECORATE_RENDER.spanNameOnRender(mv)).startSpan();
      DECORATE_RENDER.afterStart(span);
      DECORATE_RENDER.onRender(span, mv);
      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      final Span span = spanWithScope.getSpan();
      DECORATE_RENDER.onError(span, throwable);
      DECORATE_RENDER.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
    }

    // Make this advice match consistently with HandlerAdapterInstrumentation
    private void muzzleCheck(final HandlerMethod method) {
      method.getMethod();
    }
  }

  public static class ErrorHandlerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void nameResource(@Advice.Argument(3) final Exception exception) {
      final Span span = TRACER.getCurrentSpan();
      if (span.getContext().isValid() && exception != null) {
        // We want to capture the stacktrace, but that doesn't mean it should be an error.
        // We rely on a decorator to set the error state based on response code. (5xx -> error)
        DECORATE.addThrowable(span, exception);
      }
    }
  }
}
