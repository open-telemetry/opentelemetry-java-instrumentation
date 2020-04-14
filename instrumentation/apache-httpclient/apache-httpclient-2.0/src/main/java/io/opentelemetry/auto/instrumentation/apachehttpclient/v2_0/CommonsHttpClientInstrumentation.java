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
package io.opentelemetry.auto.instrumentation.apachehttpclient.v2_0;

import static io.opentelemetry.auto.instrumentation.apachehttpclient.v2_0.CommonsHttpClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.apachehttpclient.v2_0.CommonsHttpClientDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.apachehttpclient.v2_0.HttpHeadersInjectAdapter.SETTER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;

@AutoService(Instrumenter.class)
public class CommonsHttpClientInstrumentation extends Instrumenter.Default {

  public CommonsHttpClientInstrumentation() {
    super("apache-httpclient");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("org.apache.commons.httpclient.HttpClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.commons.httpclient.HttpClient"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".CommonsHttpClientDecorator", packageName + ".HttpHeadersInjectAdapter",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {

    return singletonMap(
        isMethod()
            .and(named("executeMethod"))
            .and(takesArguments(3))
            .and(takesArgument(1, named("org.apache.commons.httpclient.HttpMethod"))),
        CommonsHttpClientInstrumentation.class.getName() + "$ExecAdvice");
  }

  public static class ExecAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope methodEnter(@Advice.Argument(1) final HttpMethod httpMethod) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      final Span span =
          TRACER
              .spanBuilder(DECORATE.spanNameForRequest(httpMethod))
              .setSpanKind(CLIENT)
              .startSpan();

      DECORATE.afterStart(span);
      DECORATE.onRequest(span, httpMethod);

      final Context context = withSpan(span, Context.current());
      OpenTelemetry.getPropagators().getHttpTextFormat().inject(context, httpMethod, SETTER);
      final Scope scope = withScopedContext(context);

      return new SpanWithScope(span, scope);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Argument(1) final HttpMethod httpMethod,
        @Advice.Thrown final Throwable throwable) {

      if (spanWithScope == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(HttpClient.class);

      try {
        final Span span = spanWithScope.getSpan();
        DECORATE.onResponse(span, httpMethod);
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
      } finally {
        spanWithScope.closeScope();
      }
    }
  }
}
