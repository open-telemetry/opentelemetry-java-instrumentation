package datadog.trace.instrumentation.http_url_connection;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.bootstrap.InstrumentationContext;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
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
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("java.net.HttpURLConnection"))
        // This class is a simple delegator. Skip because it does not update its `connected` field.
        .and(not(named("sun.net.www.protocol.https.HttpsURLConnectionImpl")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {HttpUrlConnectionInstrumentation.class.getName() + "$HttpURLState"};
  }

  public Map<String, String> contextStore() {
    return Collections.singletonMap(
        "java.net.HttpURLConnection", getClass().getName() + "$HttpURLState");
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    return Collections.<ElementMatcher, String>singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("connect").or(named("getOutputStream")).or(named("getInputStream"))),
        HttpUrlConnectionAdvice.class.getName());
  }

  public static class HttpUrlConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.This final HttpURLConnection thiz,
        @Advice.FieldValue("connected") final boolean connected,
        @Advice.Origin("#m") final String methodName) {

      final HttpURLState state =
          InstrumentationContext.get(thiz, HttpURLConnection.class, HttpURLState.class);

      String operationName = "http.request";

      switch (methodName) {
        case "connect":
          if (connected) {
            return null;
          }
          /*
           * Ideally, we would like to only have a single span for each of the output and input streams,
           * but since headers are also sent on connect(), there wouldn't be a span to mark as parent if
           * we don't create a span here.
           */
          operationName += ".connect";
          break;

        case "getOutputStream":
          if (state.calledOutputStream) {
            return null;
          }
          state.calledOutputStream = true;
          operationName += ".output-stream";
          break;

        case "getInputStream":
          if (state.calledInputStream) {
            return null;
          }
          state.calledInputStream = true;
          operationName += ".input-stream";
          break;
      }

      /*
       * AgentWriter uses HttpURLConnection to report to the trace-agent. We don't want to trace
       * those requests. Check after the connected test above because getRequestProperty will
       * throw an exception if already connected.
       */
      final boolean isTraceRequest =
          Thread.currentThread().getName().equals("dd-agent-writer")
              || (!connected && thiz.getRequestProperty("Datadog-Meta-Lang") != null);
      if (isTraceRequest) {
        return null;
      }

      final Tracer tracer = GlobalTracer.get();
      if (tracer.activeSpan() == null) {
        // We don't want this as a top level span.
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
        /*
         * responseCode field cache is sometimes not populated.
         * We can't call getResponseCode() due to some unwanted side-effects
         * (e.g. breaks getOutputStream).
         */
        Tags.HTTP_STATUS.set(span, responseCode);
      }
      scope.close();
      CallDepthThreadLocalMap.reset(HttpURLConnection.class);
    }
  }

  public static class HttpURLState {
    public boolean calledOutputStream = false;
    public boolean calledInputStream = false;
  }
}
