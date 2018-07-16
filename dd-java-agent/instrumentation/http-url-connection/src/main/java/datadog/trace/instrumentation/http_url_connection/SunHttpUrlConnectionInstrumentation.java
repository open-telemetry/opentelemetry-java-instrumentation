package datadog.trace.instrumentation.http_url_connection;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.*;

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
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.LoggerFactory;

@AutoService(Instrumenter.class)
public class SunHttpUrlConnectionInstrumentation extends Instrumenter.Default {

  public SunHttpUrlConnectionInstrumentation() {
    super("httpurlconnection", "sun-httpurlconnection");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher typeMatcher() {
    return isSubTypeOf(java.net.HttpURLConnection.class)
        .and(is(sun.net.www.protocol.http.HttpURLConnection.class));
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
                    .or(named("getOutputStream"))
                    .or(named("getInputStream"))
                    .or(nameStartsWith("getHeaderField"))),
        HttpUrlConnectionAdvice.class.getName());
    return transformers;
  }

  public static class HttpUrlConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.This final sun.net.www.protocol.http.HttpURLConnection connection,
        @Advice.FieldValue("connected") final boolean connected,
        @Advice.FieldValue("responseCode") final int responseCode,
        @Advice.FieldValue("responseMessage") final String responseMessage,
        @Advice.FieldValue("inputStream") final InputStream inputStream,
        @Advice.FieldValue("cachedInputStream") final InputStream cachedInputStream,
        @Advice.Origin("#m") final String methodName) {

      LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class)
          .warn("ENTERING-----------------");
      LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class)
          .warn("connected? " + connected);
            LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class).warn("responseCode: " + responseCode);
      LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class).warn("responseMessage: " + responseMessage);
      LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class).warn("cachedInputStream: " + cachedInputStream);
            LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class).warn("cachedInputStream: " + cachedInputStream);
      LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class)
          .warn("connection: " + connection.toString() + ", HASH: " + connection.hashCode());

      final boolean isTraceRequest =
          Thread.currentThread().getName().equals("dd-agent-writer")
              || connection.getRequestProperty("Datadog-Meta-Lang") != null;
      if (isTraceRequest) {
        return null;
      }

      // inputstream not null, so don't start span
      if (cachedInputStream != null || inputStream != null) {
        LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class)
            .warn("span not started because INPUTSTREAM NOT NULL ");
        return null;
      }

      if (responseCode <= 0 && responseMessage == null) {
        final int callDepth =
            CallDepthThreadLocalMap.incrementCallDepth(
                sun.net.www.protocol.http.HttpURLConnection.class);
        if (callDepth > 0) {
          LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class)
              .warn("span not started because callDepth " + callDepth);
          return null;
        }

        LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class)
            .warn("CREATING-----------------");
        String protocol = "url";
        if (connection != null) {
          final URL url = connection.getURL();
          protocol = url.getProtocol();
        }

        try {
          throw new Exception("A");
        } catch (Exception e) {
          e.printStackTrace();
        }

        String command = ".request.response_code";
        if (methodName.equals("getOutputStream")) {
          command = ".request.output_stream";
        } else if (methodName.equals("getInputStream")) {
          command = ".request.input_stream";
        }

        final Tracer tracer = GlobalTracer.get();
        final Scope scope =
            tracer
                .buildSpan(protocol + command)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT)
                .startActive(true);

        return scope;
      } else {
        return null;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final sun.net.www.protocol.http.HttpURLConnection httpURLConnection,
        @Advice.FieldValue("responseCode") final int responseCode,
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable) {
      LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class)
          .warn("EXITING-----------------");
      if (scope == null) {
        return;
      }

      if (httpURLConnection != null) {
        final URL url = httpURLConnection.getURL();
        final Span span = scope.span();
        Tags.COMPONENT.set(scope.span(), httpURLConnection.getClass().getSimpleName());
        Tags.HTTP_URL.set(span, url.toString());
        Tags.PEER_HOSTNAME.set(span, url.getHost());
        if (url.getPort() > 0) {
          Tags.PEER_PORT.set(span, url.getPort());
        }

        Tags.HTTP_METHOD.set(span, httpURLConnection.getRequestMethod());
        final Tracer tracer = GlobalTracer.get();
        if (httpURLConnection == null) {
          tracer.inject(
              span.context(),
              Format.Builtin.HTTP_HEADERS,
              new MessageHeadersInjectAdapter(httpURLConnection));
        }
      }

      LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class)
          .warn("CLOSING-----------------");

      // should consider using getResponseCode to get a better response code.
      int rescode = -2;
      //      try {
      //        rescode = httpURLConnection.getResponseCode();
      //      } catch (Exception e) {
      //        //we
      //      }

      final Span span = scope.span();
      LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class)
          .warn("responseCode: " + responseCode);
      LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class)
          .warn("rescode: " + rescode);
      if (throwable != null) {
        LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class)
            .warn("DERROR " + throwable.getMessage());
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      } else if (responseCode > 0) {
        LoggerFactory.getLogger(sun.net.www.protocol.http.HttpURLConnection.class)
            .warn("setting tag res code: " + responseCode);
        Tags.HTTP_STATUS.set(span, responseCode);
      }
      scope.close();
      CallDepthThreadLocalMap.reset(sun.net.www.protocol.http.HttpURLConnection.class);
    }
  }
}
