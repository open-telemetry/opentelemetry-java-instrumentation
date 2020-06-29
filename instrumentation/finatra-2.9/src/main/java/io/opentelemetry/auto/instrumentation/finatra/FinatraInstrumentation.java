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

package io.opentelemetry.auto.instrumentation.finatra;

import static io.opentelemetry.auto.instrumentation.finatra.FinatraDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.finatra.FinatraDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
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
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpStatusConverter;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
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
      packageName + ".FinatraDecorator", FinatraInstrumentation.class.getName() + "$Listener"
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
        @Advice.FieldValue("routeInfo") final RouteInfo routeInfo,
        @Advice.FieldValue("clazz") final Class clazz) {

      final Span parent = TRACER.getCurrentSpan();
      parent.updateName(routeInfo.path());

      final Span span = TRACER.spanBuilder(DECORATE.spanNameForClass(clazz)).startSpan();
      DECORATE.afterStart(span);

      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void setupCallback(
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return final Some<Future<Response>> responseOption) {

      if (spanWithScope == null) {
        return;
      }

      final Span span = spanWithScope.getSpan();
      if (throwable != null) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
        spanWithScope.closeScope();
        return;
      }

      responseOption.get().addEventListener(new Listener(spanWithScope));
    }
  }

  public static class Listener implements FutureEventListener<Response> {
    private final SpanWithScope spanWithScope;

    public Listener(final SpanWithScope spanWithScope) {
      this.spanWithScope = spanWithScope;
    }

    @Override
    public void onSuccess(final Response response) {
      final Span span = spanWithScope.getSpan();

      // Don't use DECORATE.onResponse because this is the controller span
      span.setStatus(HttpStatusConverter.statusFromHttpStatus(DECORATE.status(response)));

      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
    }

    @Override
    public void onFailure(final Throwable cause) {
      final Span span = spanWithScope.getSpan();
      DECORATE.onError(span, cause);
      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
    }
  }
}
