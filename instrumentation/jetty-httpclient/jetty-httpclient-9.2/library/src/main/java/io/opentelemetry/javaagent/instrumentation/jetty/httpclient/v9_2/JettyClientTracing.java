package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2.JettyClientWrapUtil.wrapResponseListeners;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.List;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * JettyClientTracing Designed for use outside of Javaagent. Use the static creation methods to
 * build a Jetty HttpClient that is wrapped with tracing enabled
 */
public class JettyClientTracing extends HttpClient {

  private final Instrumenter<Request, Response> instrumenter;

  public static HttpClient create(OpenTelemetry openTelemetry) {
    return new JettyClientTracing(openTelemetry);
  }

  public static HttpClient create(
      OpenTelemetry openTelemetry, SslContextFactory sslContextFactory) {
    return new JettyClientTracing(openTelemetry, sslContextFactory);
  }

  public static HttpClient create(
      OpenTelemetry openTelemetry,
      HttpClientTransport transport,
      SslContextFactory sslContextFactory) {
    return new JettyClientTracing(openTelemetry, transport, sslContextFactory);
  }

  public static HttpClient from(OpenTelemetry openTelemetry, HttpClient originalClient) {
    return create(
        openTelemetry, originalClient.getTransport(), originalClient.getSslContextFactory());
  }

  private JettyClientTracing(OpenTelemetry openTelemetry) {
    this.instrumenter = buildInstrumenter(openTelemetry);
  }

  private JettyClientTracing(OpenTelemetry openTelemetry, SslContextFactory sslContextFactory) {
    super(sslContextFactory);
    this.instrumenter = buildInstrumenter(openTelemetry);
  }

  private JettyClientTracing(
      OpenTelemetry openTelemetry,
      HttpClientTransport transport,
      SslContextFactory sslContextFactory) {
    super(transport, sslContextFactory);
    this.instrumenter = buildInstrumenter(openTelemetry);
  }

  private static Instrumenter<Request, Response> buildInstrumenter(OpenTelemetry openTelemetry) {
    JettyClientInstrumenterBuilder buildier = new JettyClientInstrumenterBuilder(openTelemetry);
    return buildier.build();
  }

  @Override
  protected void send(HttpRequest request, List<Response.ResponseListener> listeners) {
    Context parentContext = Context.current();
    JettyHttpClient9TracingInterceptor requestInterceptor =
        new JettyHttpClient9TracingInterceptor(parentContext, this.instrumenter);
    requestInterceptor.attachToRequest(request);
    List<Response.ResponseListener> wrapped = wrapResponseListeners(parentContext, listeners);
    super.send(request, wrapped);
  }
}
