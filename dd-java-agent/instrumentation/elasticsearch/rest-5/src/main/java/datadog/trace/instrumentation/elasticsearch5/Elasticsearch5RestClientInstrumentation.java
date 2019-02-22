package datadog.trace.instrumentation.elasticsearch5;

import static datadog.trace.instrumentation.elasticsearch.ElasticsearchRestClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
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
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.DatabaseClientDecorator",
      "datadog.trace.instrumentation.elasticsearch.ElasticsearchRestClientDecorator",
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
        ElasticsearchRestClientAdvice.class.getName());
  }

  public static class ElasticsearchRestClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.Argument(0) final String method,
        @Advice.Argument(1) final String endpoint,
        @Advice.Argument(value = 5, readOnly = false) ResponseListener responseListener) {

      final Scope scope =
          GlobalTracer.get().buildSpan("elasticsearch.rest.query").startActive(false);
      DECORATE.afterStart(scope.span());
      DECORATE.onRequest(scope.span(), method, endpoint);

      responseListener = new RestResponseListener(responseListener, scope.span());
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        final Span span = scope.span();
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.finish();
      }
      scope.close();
    }
  }
}
