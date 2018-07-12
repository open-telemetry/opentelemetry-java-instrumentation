package datadog.trace.instrumentation.apachehttpclient;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.execchain.ClientExecChain;

@AutoService(Instrumenter.class)
public class ApacheHttpClientInstrumentation extends Instrumenter.Default {

  public ApacheHttpClientInstrumentation() {
    super("httpclient");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.apache.http.impl.client.HttpClientBuilder");
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
        "org.apache.http.impl.execchain.ClientExecChain");
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
    Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(named("decorateProtocolExec")), ApacheHttpClientAdvice.class.getName());
    return transformers;
  }

  public static class ApacheHttpClientAdvice {
    /** Strategy: add our tracing exec to the apache exec chain. */
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addTracingExec(@Advice.Return(readOnly = false) ClientExecChain execChain) {
      execChain =
          new DDTracingClientExec(
              execChain, DefaultRedirectStrategy.INSTANCE, false, GlobalTracer.get());
    }
  }
}
