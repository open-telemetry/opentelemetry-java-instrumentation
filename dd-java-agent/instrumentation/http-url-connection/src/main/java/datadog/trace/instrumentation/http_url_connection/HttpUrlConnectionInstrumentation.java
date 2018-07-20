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
import java.util.WeakHashMap;
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
      "datadog.trace.instrumentation.http_url_connection.MessageHeadersInjectAdapter",
      HttpUrlConnectionInstrumentation.class.getName() + "$HttpURLState"
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
        @Advice.FieldValue("connected") final boolean connected,
        @Advice.Origin("#m") final String methodName) {

      final HttpURLState state = HttpURLState.get(thiz);
      String operationName = "http.request";
      if ("connect".equals(methodName)) {
        if (connected) {
          return null;
        }
        // We get here in cases where connect() is called first.
        // We need to inject headers now because after connected=true no more headers can be added.

        // In total there will be two spans:
        // - one for the connect() which does propagation
        // - one for the input or output stream (presumably called after connect())
        operationName += ".connect";
      } else {
        if (state.hasDoneIO()) {
          return null;
        }
        state.setHasDoneIO(true);
      }

      // AgentWriter uses HttpURLConnection to report to the trace-agent. We don't want to trace
      // those requests.
      // Check after the connected test above because getRequestProperty will throw an exception if
      // already connected.
      final boolean isTraceRequest =
          Thread.currentThread().getName().equals("dd-agent-writer")
              || (!connected && thiz.getRequestProperty("Datadog-Meta-Lang") != null);
      if (isTraceRequest) {
        return null;
      }

      final Tracer tracer = GlobalTracer.get();
      if (tracer.activeSpan() == null) {
        // httpurlconnection doesn't play nicely with top-level spans
        return null;
      }

      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpURLConnection.class);
      if (callDepth > 0) {
        return null;
      }

      final Scope scope =
          tracer
              .buildSpan(operationName)
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
      } else if (responseCode > 0) {
        // responseCode field cache is sometimes not populated.
        // We can't call getResponseCode() due to some unwanted side-effects (e.g. breaks
        // getOutputStream).
        Tags.HTTP_STATUS.set(span, responseCode);
      }
      scope.close();
      CallDepthThreadLocalMap.reset(HttpURLConnection.class);
    }
  }

  public static class HttpURLState {
    private static final Map<HttpURLConnection, HttpURLState> STATE_MAP =
        Collections.synchronizedMap(new WeakHashMap<HttpURLConnection, HttpURLState>());

    public static HttpURLState get(HttpURLConnection connection) {
      HttpURLState state = STATE_MAP.get(connection);
      if (state == null) {
        // not thread-safe, but neither is HttpURLConnection
        state = new HttpURLState();
        STATE_MAP.put(connection, state);
      }
      return state;
    }

    public boolean hasDoneIO = false;

    private HttpURLState() {}

    public boolean hasDoneIO() {
      return hasDoneIO;
    }

    public void setHasDoneIO(boolean value) {
      this.hasDoneIO = value;
    }
  }
}
