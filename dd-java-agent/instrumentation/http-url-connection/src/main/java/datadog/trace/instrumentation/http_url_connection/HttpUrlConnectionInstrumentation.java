package datadog.trace.instrumentation.http_url_connection;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class HttpUrlConnectionInstrumentation extends Instrumenter.Default {

  public HttpUrlConnectionInstrumentation() {
    super("httpurlconnection");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher typeMatcher() {
    return isSubTypeOf(HttpURLConnection.class)
        // This class is a simple delegator. Skip because it does not update its `connected` field.
        .and(not(named("sun.net.www.protocol.https.HttpsURLConnectionImpl")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.http_url_connection.MessageHeadersInjectAdapter"
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(
                named("getResponseCode")
                    .or(named("connect"))
                    .or(named("getOutputStream"))
                    .or(named("getInputStream"))
                    .or(nameStartsWith("getHeaderField"))),
        HttpUrlConnectionAdvice.class.getName());
    return transformers;
  }

  public static class HttpUrlConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.This final HttpURLConnection thiz,
        @Advice.FieldValue("connected") final boolean connected) {

      // This works with keep-alive because the "connected" flag is not initialized until the socket cache is checked.
      // This allows us to use the connected flag to know if this instance has done any io.
      if (connected) {
        return null;
      }

      // AgentWriter uses HttpURLConnection to report to the trace-agent. We don't want to trace those requests.
      // Check after the connected test above because getRequestProperty will throw an exception if already connected.
      final boolean isTraceRequest =
          Thread.currentThread().getName().equals("dd-agent-writer")
              || thiz.getRequestProperty("Datadog-Meta-Lang") != null;
      if (isTraceRequest) {
        return null;
      }

      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpURLConnection.class);
      if (callDepth > 0) {
        return null;
      }

      final String protocol = thiz.getURL().getProtocol();

      final Tracer tracer = GlobalTracer.get();
      final Scope scope =
          tracer
              .buildSpan("http.request")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT)
              .startActive(true);

      final URL url = thiz.getURL();
      final Span span = scope.span();
      Tags.COMPONENT.set(scope.span(), thiz.getClass().getSimpleName());
      Tags.HTTP_URL.set(span, url.toString());
      Tags.PEER_HOSTNAME.set(span, url.getHost());
      if (url.getPort() > 0) {
        Tags.PEER_PORT.set(span, url.getPort());
      } else if (thiz instanceof HttpsURLConnection) {
        Tags.PEER_PORT.set(span, 443);
      } else {
        Tags.PEER_PORT.set(span, 80);
      }
      Tags.HTTP_METHOD.set(span, thiz.getRequestMethod());

      tracer.inject(
          span.context(), Format.Builtin.HTTP_HEADERS, new MessageHeadersInjectAdapter(thiz));
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.FieldValue("responseCode") final int responseCode,
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable) {

      if (scope == null) {
        return;
      }

      final Span span = scope.span();
      if (throwable != null) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      } else {
        if (responseCode > 0) {
          // responseCode field cache is sometimes not populated.
          // We can't call getResponseCode() due to some unwanted side-effects (e.g. breaks getOutputStream).
          Tags.HTTP_STATUS.set(span, responseCode);
        }
      }
      scope.close();
      CallDepthThreadLocalMap.reset(HttpURLConnection.class);
    }
  }
}
