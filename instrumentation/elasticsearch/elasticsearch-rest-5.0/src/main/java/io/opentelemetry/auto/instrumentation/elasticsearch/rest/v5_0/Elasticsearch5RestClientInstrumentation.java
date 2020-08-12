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

package io.opentelemetry.auto.instrumentation.elasticsearch.rest.v5_0;

import static io.opentelemetry.auto.instrumentation.elasticsearch.rest.ElasticsearchRestClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.elasticsearch.rest.ElasticsearchRestClientDecorator.TRACER;
import static io.opentelemetry.auto.tooling.matcher.NameMatchers.namedOneOf;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.client.ResponseListener;

@AutoService(Instrumenter.class)
public class Elasticsearch5RestClientInstrumentation extends Instrumenter.Default {

  public Elasticsearch5RestClientInstrumentation() {
    super("elasticsearch", "elasticsearch-rest", "elasticsearch-rest-5");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.instrumentation.elasticsearch.rest.ElasticsearchRestClientDecorator",
      packageName + ".RestResponseListener",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.elasticsearch.client.RestClient");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(namedOneOf("performRequestAsync", "performRequestAsyncNoCatch"))
            .and(takesArguments(7))
            .and(takesArgument(0, named("java.lang.String"))) // method
            .and(takesArgument(1, named("java.lang.String"))) // endpoint
            .and(takesArgument(5, named("org.elasticsearch.client.ResponseListener"))),
        Elasticsearch5RestClientInstrumentation.class.getName() + "$ElasticsearchRestClientAdvice");
  }

  public static class ElasticsearchRestClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(
        @Advice.Argument(0) final String method,
        @Advice.Argument(1) final String endpoint,
        @Advice.Argument(value = 5, readOnly = false) ResponseListener responseListener) {

      Span span = TRACER.spanBuilder(method + " " + endpoint).startSpan();
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, method, endpoint);

      responseListener = new RestResponseListener(responseListener, span);

      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        Span span = spanWithScope.getSpan();
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
      }
      spanWithScope.closeScope();
      // span finished by RestResponseListener
    }
  }
}
