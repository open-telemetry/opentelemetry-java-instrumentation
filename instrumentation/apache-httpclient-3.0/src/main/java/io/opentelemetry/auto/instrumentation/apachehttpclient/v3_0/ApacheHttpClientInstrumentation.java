package io.opentelemetry.auto.instrumentation.apachehttpclient.v3_0;

import static io.opentelemetry.auto.instrumentation.apachehttpclient.v3_0.ApacheHttpClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.apachehttpclient.v3_0.ApacheHttpClientDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.apachehttpclient.v3_0.HttpHeadersInjectAdapter.SETTER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
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
public class ApacheHttpClientInstrumentation extends Instrumenter.Default {

  public ApacheHttpClientInstrumentation() {
    super("httpclient", "apache-httpclient", "apache-http-client");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("org.apache.commons.httpclient.HttpClient"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".HttpHeadersInjectAdapter",
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.HttpClientDecorator",
      packageName + ".ApacheHttpClientDecorator"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("executeMethod"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.commons.httpclient.HostConfiguration")))
            .and(takesArgument(1, named("org.apache.commons.httpclient.HttpMethod")))
            .and(takesArgument(2, named("org.apache.commons.httpclient.HttpState"))),
        ApacheHttpClientInstrumentation.class.getName() + "$ExecuteAdvice");
  }

  public static class ExecuteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope methodEnter(@Advice.Argument(1) final HttpMethod httpMethod) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }
      final Span span = TRACER.spanBuilder("http.request").setSpanKind(CLIENT).startSpan();
      final Scope scope = TRACER.withSpan(span);

      DECORATE.afterStart(span);
      DECORATE.onRequest(span, httpMethod);

      final boolean awsClientCall =
          httpMethod.getRequestHeaders("amz-sdk-invocation-id").length > 0;
      // AWS calls are often signed, so we can't add headers without breaking the signature.
      if (!awsClientCall) {
        TRACER.getHttpTextFormat().inject(span.getContext(), httpMethod, SETTER);
      }
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
