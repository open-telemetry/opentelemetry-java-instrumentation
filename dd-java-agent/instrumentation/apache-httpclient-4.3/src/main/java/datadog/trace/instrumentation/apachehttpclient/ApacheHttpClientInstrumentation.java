package datadog.trace.instrumentation.apachehttpclient;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.impl.execchain.ClientExecChain;

@AutoService(Instrumenter.class)
public class ApacheHttpClientInstrumentation extends Instrumenter.Default {

  public ApacheHttpClientInstrumentation() {
    super("httpclient");
  }

  @Override
  public ElementMatcher typeMatcher() {
    return named("org.apache.http.impl.client.HttpClientBuilder")
        .or(hasSuperType(named("org.apache.http.impl.client.CloseableHttpClient")));
  }

  @Override
  public ElementMatcher<? super ClassLoader> classLoaderMatcher() {
    return classLoaderHasClasses(
        "org.apache.http.HttpException",
        "org.apache.http.HttpRequest",
        "org.apache.http.client.RedirectStrategy",
        "org.apache.http.client.methods.CloseableHttpResponse",
        "org.apache.http.client.methods.HttpExecutionAware",
        "org.apache.http.client.methods.HttpRequestWrapper",
        "org.apache.http.client.protocol.HttpClientContext",
        "org.apache.http.conn.routing.HttpRoute",
        "org.apache.http.impl.execchain.ClientExecChain",
        "org.apache.http.impl.client.CloseableHttpClient",
        "org.apache.http.impl.client.InternalHttpClient");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.apachehttpclient.DDTracingClientExec",
      "datadog.trace.instrumentation.apachehttpclient.DDTracingClientExec$HttpHeadersInjectAdapter"
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(not(isAbstract())).and(named("doExecute")), ClientAdvice.class.getName());
    transformers.put(
        isMethod().and(named("decorateProtocolExec")), ClientExecAdvice.class.getName());
    return transformers;
  }

  public static class ClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope methodEnter() {
      final Tracer.SpanBuilder spanBuilder =
          GlobalTracer.get()
              .buildSpan(DDTracingClientExec.OPERATION_NAME)
              .withTag(Tags.COMPONENT.getKey(), DDTracingClientExec.COMPONENT_NAME);
      return spanBuilder.startActive(true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      final Span span = scope.span();
      if (throwable != null) {
        Tags.ERROR.set(span, Boolean.TRUE);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
        span.finish();
      }
      scope.close();
    }
  }

  public static class ClientExecAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addTracingExec(@Advice.Return(readOnly = false) ClientExecChain execChain) {
      execChain = new DDTracingClientExec(execChain, GlobalTracer.get());
    }
  }
}
