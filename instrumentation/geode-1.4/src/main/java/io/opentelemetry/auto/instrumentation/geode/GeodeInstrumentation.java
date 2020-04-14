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
package io.opentelemetry.auto.instrumentation.geode;

import static io.opentelemetry.auto.instrumentation.geode.GeodeDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.geode.GeodeDecorator.TRACER;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.hasInterface;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.geode.cache.Region;

@AutoService(Instrumenter.class)
public class GeodeInstrumentation extends Instrumenter.Default {
  public GeodeInstrumentation() {
    super("geode", "geode-client");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return hasInterface(named("org.apache.geode.cache.Region"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".GeodeDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> map = new HashMap<>(2);
    map.put(
        isMethod()
            .and(
                named("clear")
                    .or(nameStartsWith("contains"))
                    .or(named("create"))
                    .or(named("destroy"))
                    .or(named("entrySet"))
                    .or(named("get"))
                    .or(named("getAll"))
                    .or(named("invalidate"))
                    .or(nameStartsWith("keySet"))
                    .or(nameStartsWith("put"))
                    .or(nameStartsWith("remove"))
                    .or(named("replace"))),
        GeodeInstrumentation.class.getName() + "$SimpleAdvice");
    map.put(
        isMethod()
            .and(named("existsValue").or(named("query")).or(named("selectValue")))
            .and(takesArgument(0, named("java.lang.String"))),
        GeodeInstrumentation.class.getName() + "$QueryAdvice");
    return map;
  }

  public static class SimpleAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(
        @Advice.This final Region thiz, @Advice.Origin final Method method) {
      if (CallDepthThreadLocalMap.incrementCallDepth(Region.class) > 0) {
        return null;
      }
      final Span span = TRACER.spanBuilder(method.getName()).setSpanKind(CLIENT).startSpan();
      DECORATE.afterStart(span);
      span.setAttribute(Tags.DB_INSTANCE, thiz.getName());
      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      try {
        final Span span = spanWithScope.getSpan();
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
        spanWithScope.closeScope();
      } finally {
        CallDepthThreadLocalMap.reset(Region.class);
      }
    }
  }

  public static class QueryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(
        @Advice.This final Region thiz,
        @Advice.Origin final Method method,
        @Advice.Argument(0) final String query) {
      if (CallDepthThreadLocalMap.incrementCallDepth(Region.class) > 0) {
        return null;
      }
      final Span span = TRACER.spanBuilder(method.getName()).setSpanKind(CLIENT).startSpan();
      DECORATE.afterStart(span);
      span.setAttribute(Tags.DB_INSTANCE, thiz.getName());
      span.setAttribute(Tags.DB_STATEMENT, query);
      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      try {
        final Span span = spanWithScope.getSpan();
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
        spanWithScope.closeScope();
      } finally {
        CallDepthThreadLocalMap.reset(Region.class);
      }
    }
  }
}
