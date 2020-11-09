/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import static io.opentelemetry.javaagent.instrumentation.finatra.FinatraTracer.tracer;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import com.twitter.finagle.http.Response;
import com.twitter.finatra.http.contexts.RouteInfo;
import com.twitter.util.Future;
import com.twitter.util.FutureEventListener;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.Some;

@AutoService(Instrumenter.class)
public class FinatraInstrumentation extends Instrumenter.Default {
  public FinatraInstrumentation() {
    super("finatra");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".FinatraTracer", FinatraInstrumentation.class.getName() + "$Listener"
    };
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("com.twitter.finatra.http.internal.routing.Route");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return nameStartsWith("com.twitter.finatra.")
        .<TypeDescription>and(
            extendsClass(named("com.twitter.finatra.http.internal.routing.Route")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("handleMatch"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("com.twitter.finagle.http.Request"))),
        FinatraInstrumentation.class.getName() + "$RouteAdvice");
  }

  public static class RouteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope nameSpan(
        @Advice.FieldValue("routeInfo") RouteInfo routeInfo,
        @Advice.FieldValue("clazz") Class clazz) {

      Span serverSpan = BaseTracer.getCurrentServerSpan();
      if (serverSpan != null) {
        serverSpan.updateName(routeInfo.path());
      }

      Span span = tracer().startSpan(clazz);

      return new SpanWithScope(span, span.makeCurrent());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void setupCallback(
        @Advice.Enter SpanWithScope spanWithScope,
        @Advice.Thrown Throwable throwable,
        @Advice.Return Some<Future<Response>> responseOption) {

      if (spanWithScope == null) {
        return;
      }

      Span span = spanWithScope.getSpan();
      if (throwable != null) {
        tracer().endExceptionally(span, throwable);
        spanWithScope.closeScope();
        return;
      }

      responseOption.get().addEventListener(new Listener(spanWithScope));
    }
  }

  public static class Listener implements FutureEventListener<Response> {
    private final SpanWithScope spanWithScope;

    public Listener(SpanWithScope spanWithScope) {
      this.spanWithScope = spanWithScope;
    }

    @Override
    public void onSuccess(Response response) {
      Span span = spanWithScope.getSpan();
      tracer().end(span);
      spanWithScope.closeScope();
    }

    @Override
    public void onFailure(Throwable cause) {
      Span span = spanWithScope.getSpan();
      tracer().endExceptionally(span, cause);
      spanWithScope.closeScope();
    }
  }
}
