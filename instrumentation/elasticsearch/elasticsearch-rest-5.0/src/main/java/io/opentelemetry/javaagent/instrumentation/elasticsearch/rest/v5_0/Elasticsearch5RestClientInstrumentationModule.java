/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v5_0;

import static io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.ElasticsearchRestClientTracer.tracer;
import static io.opentelemetry.javaagent.tooling.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.client.ResponseListener;

@AutoService(InstrumentationModule.class)
public class Elasticsearch5RestClientInstrumentationModule extends InstrumentationModule {
  public Elasticsearch5RestClientInstrumentationModule() {
    super("elasticsearch", "elasticsearch-rest", "elasticsearch-rest-5");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.ElasticsearchRestClientTracer",
      packageName + ".RestResponseListener",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RestClientInstrumentation());
  }

  private static final class RestClientInstrumentation implements TypeInstrumentation {
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
          Elasticsearch5RestClientInstrumentationModule.class.getName()
              + "$ElasticsearchRestClientAdvice");
    }
  }

  public static class ElasticsearchRestClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) String method,
        @Advice.Argument(1) String endpoint,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Argument(value = 5, readOnly = false) ResponseListener responseListener) {

      span = tracer().startSpan(null, method + " " + endpoint);
      scope = tracer().startScope(span);

      tracer().onRequest(span, method, endpoint);
      responseListener = new RestResponseListener(responseListener, span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      if (throwable != null) {
        tracer().endExceptionally(span, throwable);
      }
    }
  }
}
