/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import static io.opentelemetry.javaagent.instrumentation.geode.GeodeTracer.TRACER;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.hasInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.tooling.Instrumenter;
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
      packageName + ".GeodeTracer",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> map = new HashMap<>(2);
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
    public static void onEnter(
        @Advice.This Region<?, ?> thiz,
        @Advice.Origin Method method,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      if (CallDepthThreadLocalMap.incrementCallDepth(Region.class) > 0) {
        return;
      }
      span = TRACER.startSpan(method.getName(), thiz, null);
      scope = TRACER.startScope(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();

      CallDepthThreadLocalMap.reset(Region.class);
      if (throwable != null) {
        TRACER.endExceptionally(span, throwable);
      } else {
        TRACER.end(span);
      }
    }
  }

  public static class QueryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Region<?, ?> thiz,
        @Advice.Origin Method method,
        @Advice.Argument(0) String query,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      if (CallDepthThreadLocalMap.incrementCallDepth(Region.class) > 0) {
        return;
      }
      span = TRACER.startSpan(method.getName(), thiz, query);
      scope = TRACER.startScope(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();

      CallDepthThreadLocalMap.reset(Region.class);
      if (throwable != null) {
        TRACER.endExceptionally(span, throwable);
      } else {
        TRACER.end(span);
      }
    }
  }
}
