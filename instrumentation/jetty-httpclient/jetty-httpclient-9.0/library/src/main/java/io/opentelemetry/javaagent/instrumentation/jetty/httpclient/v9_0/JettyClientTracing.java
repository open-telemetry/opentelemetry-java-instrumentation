package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_0;

import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_0.JettyClientWrapUtil.wrapAndStartTracer;

import io.opentelemetry.context.Context;
import java.net.URI;
import java.util.List;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class JettyClientTracing extends HttpClient {

  public static HttpClient create() {
    return new JettyClientTracing();
  }

  public static HttpClient create(SslContextFactory sslContextFactory) {
    return new JettyClientTracing(sslContextFactory);
  }

  public static HttpClient create(
      HttpClientTransport transport, SslContextFactory sslContextFactory) {
    return new JettyClientTracing(transport, sslContextFactory);
  }

  private JettyClientTracing() {}

  private JettyClientTracing(SslContextFactory sslContextFactory) {
    super(sslContextFactory);
  }

  private JettyClientTracing(HttpClientTransport transport, SslContextFactory sslContextFactory) {
    super(transport, sslContextFactory);
  }

  public static HttpClient from(HttpClient originalClient) {
    return create(originalClient.getTransport(), originalClient.getSslContextFactory());
  }

  @Override
  public Request newRequest(URI uri) {
    return super.newRequest(uri);
  }

  @Override
  protected void send(HttpRequest request, List<Response.ResponseListener> listeners) {
    Context parentContext = Context.current();
    JettyHttpClient9TracingInterceptor requestInterceptor =
        new JettyHttpClient9TracingInterceptor(parentContext);
    requestInterceptor.attachToRequest(request);
    List<Response.ResponseListener> wrapped = wrapAndStartTracer(parentContext, request, listeners);
    super.send(request, wrapped);
  }
}
