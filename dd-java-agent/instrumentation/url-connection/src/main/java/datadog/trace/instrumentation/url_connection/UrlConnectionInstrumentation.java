package datadog.trace.instrumentation.url_connection;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
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
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import javax.net.ssl.HttpsURLConnection;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import sun.net.www.protocol.ftp.FtpURLConnection;
import sun.net.www.protocol.mailto.MailToURLConnection;

@AutoService(Instrumenter.class)
public class UrlConnectionInstrumentation extends Instrumenter.Configurable {

  public UrlConnectionInstrumentation() {
    super("urlconnection", "httpurlconnection");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            not(
                    is(sun.net.www.protocol.jar.JarURLConnection.class)
                        .or(is(sun.net.www.protocol.file.FileURLConnection.class)))
                .and(isSubTypeOf(URLConnection.class)))
        .transform(
            new HelperInjector(
                "datadog.trace.instrumentation.url_connection.MessageHeadersInjectAdapter"))
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(isPublic())
                        .and(
                            named("getResponseCode")
                                .or(named("getOutputStream"))
                                .or(named("getInputStream"))
                                .or(nameStartsWith("getHeaderField"))),
                    UrlConnectionAdvice.class.getName()))
        .asDecorator();
  }

  public static class UrlConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.This final URLConnection connection, @Advice.Origin("#m") final String methodName) {
      String protocol = "url";
      if (connection != null) {
        final URL url = connection.getURL();
        protocol = url.getProtocol();
      }

      final boolean isValidProtocol =
          protocol.equals("http")
              || protocol.equals("https")
              || protocol.equals("ftp")
              || protocol.equals("mailto");
      final boolean isTraceRequest =
          Thread.currentThread().getName().equals("dd-agent-writer")
              || (connection != null && connection.getRequestProperty("Datadog-Meta-Lang") != null);
      if (!isValidProtocol || isTraceRequest) {
        return null;
      }

      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(URLConnection.class);
      if (callDepth > 0) {
        return null;
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

      if (connection != null) {
        final URL url = connection.getURL();
        final Span span = scope.span();
        Tags.COMPONENT.set(scope.span(), connection.getClass().getSimpleName());
        Tags.HTTP_URL.set(span, url.toString());
        Tags.PEER_HOSTNAME.set(span, url.getHost());
        if (url.getPort() > 0) {
          Tags.PEER_PORT.set(span, url.getPort());
        } else if (connection instanceof HttpsURLConnection) {
          Tags.PEER_PORT.set(span, 443);
        } else if (connection instanceof HttpURLConnection) {
          Tags.PEER_PORT.set(span, 80);
        } else if (connection instanceof FtpURLConnection) {
          Tags.PEER_PORT.set(span, 21);
        } else if (connection instanceof MailToURLConnection) {
          Tags.PEER_PORT.set(span, 25);
        }

        if (connection instanceof HttpURLConnection) {

          Tags.HTTP_METHOD.set(span, ((HttpURLConnection) connection).getRequestMethod());

          tracer.inject(
              span.context(),
              Format.Builtin.HTTP_HEADERS,
              new MessageHeadersInjectAdapter((HttpURLConnection) connection));
        }
      }
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final URLConnection connection,
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable) {

      if (scope == null) {
        return;
      }

      final Span span = scope.span();

      if (throwable != null) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      } else if (connection instanceof HttpURLConnection) {
        try {
          Tags.HTTP_STATUS.set(span, ((HttpURLConnection) connection).getResponseCode());
        } catch (final IOException e) {
        }
      }
      scope.close();
      CallDepthThreadLocalMap.reset(URLConnection.class);
    }
  }
}
