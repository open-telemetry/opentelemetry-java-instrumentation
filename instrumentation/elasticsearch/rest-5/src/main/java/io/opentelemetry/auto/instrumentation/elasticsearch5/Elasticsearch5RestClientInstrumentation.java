package io.opentelemetry.auto.instrumentation.elasticsearch5;

import static io.opentelemetry.auto.instrumentation.elasticsearch.ElasticsearchRestClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.elasticsearch.ElasticsearchRestClientDecorator.TRACER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.auto.tooling.Instrumenter;
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
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.DatabaseClientDecorator",
      "io.opentelemetry.auto.instrumentation.elasticsearch.ElasticsearchRestClientDecorator",
      packageName + ".RestResponseListener",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(named("org.elasticsearch.client.RestClient"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("performRequestAsync").or(named("performRequestAsyncNoCatch")))
            .and(takesArguments(7))
            .and(takesArgument(0, named("java.lang.String"))) // method
            .and(takesArgument(1, named("java.lang.String"))) // endpoint
            .and(takesArgument(5, named("org.elasticsearch.client.ResponseListener"))),
        Elasticsearch5RestClientInstrumentation.class.getName() + "$ElasticsearchRestClientAdvice");
  }

  public static class ElasticsearchRestClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanScopePair onEnter(
        @Advice.Argument(0) final String method,
        @Advice.Argument(1) final String endpoint,
        @Advice.Argument(value = 5, readOnly = false) ResponseListener responseListener) {

      final Span span = TRACER.spanBuilder("elasticsearch.rest.query").startSpan();
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, method, endpoint);

      responseListener = new RestResponseListener(responseListener, span);

      return new SpanScopePair(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanScopePair spanScopePair, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        final Span span = spanScopePair.getSpan();
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
      }
      spanScopePair.getScope().close();
    }
  }
}
