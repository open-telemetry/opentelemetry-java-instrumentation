package datadog.trace.instrumentation.elasticsearch5;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.client.ResponseListener;

@AutoService(Instrumenter.class)
public class Elasticsearch5RestClientInstrumentation extends Instrumenter.Default {

  public Elasticsearch5RestClientInstrumentation() {
    super("elasticsearch", "elasticsearch-rest", "elasticsearch-rest-5");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {"datadog.trace.instrumentation.elasticsearch5.RestResponseListener"};
  }

  @Override
  public ElementMatcher typeMatcher() {
    return not(isInterface()).and(named("org.elasticsearch.client.RestClient"));
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(named("performRequestAsync"))
            .and(takesArguments(7))
            .and(takesArgument(0, named("java.lang.String"))) // method
            .and(takesArgument(1, named("java.lang.String"))) // endpoint
            .and(takesArgument(5, named("org.elasticsearch.client.ResponseListener"))),
        ElasticsearchRestClientAdvice.class.getName());
    return transformers;
  }

  public static class ElasticsearchRestClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.Argument(0) final String method,
        @Advice.Argument(1) final String endpoint,
        @Advice.Argument(value = 5, readOnly = false) ResponseListener responseListener) {

      final Scope scope =
          GlobalTracer.get()
              .buildSpan("elasticsearch.rest.query")
              .withTag(DDTags.SERVICE_NAME, "elasticsearch")
              .withTag(Tags.HTTP_METHOD.getKey(), method)
              .withTag(Tags.HTTP_URL.getKey(), endpoint)
              .withTag(Tags.COMPONENT.getKey(), "elasticsearch-java")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
              .startActive(false);

      responseListener = new RestResponseListener(responseListener, scope.span());
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        final Span span = scope.span();
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
        span.finish();
      }
      scope.close();
    }
  }
}
