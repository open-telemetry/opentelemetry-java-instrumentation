/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_REQUIRED;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.glassfish.grizzly.memory.Buffers.wrap;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.semconv.HttpAttributes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.util.Parameters;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.IdleTimeoutFilter;
import org.junit.jupiter.api.extension.RegisterExtension;

public class GrizzlyFilterchainServerTest extends AbstractHttpServerTest<HttpServer> {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private TCPNIOTransport transport;

  @Override
  protected HttpServer setupServer() throws Exception {
    FilterChain filterChain = setUpFilterChain();
    setUpTransport(filterChain);

    transport.bind("127.0.0.1", port);
    transport.start();
    return null;
  }

  @Override
  protected void stopServer(HttpServer httpServer) throws IOException {
    transport.stop();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setHttpAttributes(
        serverEndpoint -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(HttpAttributes.HTTP_ROUTE);
          return attributes;
        });

    // justification: grizzly async closes the channel which
    // looks like a ConnectException to the client when this happens
    options.setTestException(false);

    options.setTestCaptureHttpHeaders(false);
    options.setVerifyServerSpanEndTime(
        false); // server spans are ended inside of the controller spans
    options.setTestErrorBody(false);
  }

  private void setUpTransport(FilterChain filterChain) {
    TCPNIOTransportBuilder transportBuilder =
        TCPNIOTransportBuilder.newInstance().setOptimizedForMultiplexing(true);

    transportBuilder.setTcpNoDelay(true);
    transportBuilder.setKeepAlive(false);
    transportBuilder.setReuseAddress(true);
    transportBuilder.setServerConnectionBackLog(50);
    transportBuilder.setServerSocketSoTimeout(80000);

    transport = transportBuilder.build();
    transport.setProcessor(filterChain);
  }

  @SuppressWarnings("deprecation") // until figured out what to use
  private static FilterChain setUpFilterChain() {
    return FilterChainBuilder.stateless()
        .add(createTransportFilter())
        .add(createIdleTimeoutFilter())
        .add(new HttpServerFilter()) // TODO (heyams) replace with HttpCodecFilter after CI is green
        .add(new LastFilter())
        .build();
  }

  private static TransportFilter createTransportFilter() {
    return new TransportFilter();
  }

  private static IdleTimeoutFilter createIdleTimeoutFilter() {
    return new IdleTimeoutFilter(
        new DelayedExecutor(Executors.newCachedThreadPool()), 80000, MILLISECONDS);
  }

  private static class LastFilter extends BaseFilter {
    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
      if (ctx.getMessage() instanceof HttpContent) {
        HttpContent httpContent = ctx.getMessage();
        HttpHeader httpHeader = httpContent.getHttpHeader();
        if (httpHeader instanceof HttpRequestPacket) {
          HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();
          ResponseParameters responseParameters = buildResponse(request);
          HttpResponsePacket.Builder builder =
              HttpResponsePacket.builder(request)
                  .status(responseParameters.getStatus())
                  .header(
                      "Content-Length",
                      String.valueOf(responseParameters.getResponseBody().length));
          responseParameters.fillHeaders(builder);
          HttpResponsePacket responsePacket = builder.build();
          controller(
              responseParameters.getEndpoint(),
              () -> {
                responseParameters.execute();
                ctx.write(
                    HttpContent.builder(responsePacket)
                        .content(wrap(ctx.getMemoryManager(), responseParameters.getResponseBody()))
                        .build());
                return responsePacket;
              });
        }
      }
      return ctx.getStopAction();
    }

    private ResponseParameters buildResponse(HttpRequestPacket request) {
      String uri = request.getRequestURI();
      Map<String, String> headers = new HashMap<>();

      ServerEndpoint endpoint;
      Runnable closure = null;
      switch (uri) {
        case "/success":
          endpoint = SUCCESS;
          break;
        case "/redirect":
          endpoint = REDIRECT;
          headers.put("location", REDIRECT.getBody());
          break;
        case "/error-status":
          endpoint = ERROR;
          break;
        case "/exception":
          throw new IllegalArgumentException(EXCEPTION.getBody());
        case "/query":
          endpoint = QUERY_PARAM;
          break;
        case "/path/123/param":
          endpoint = PATH_PARAM;
          break;
        case "/authRequired":
          endpoint = AUTH_REQUIRED;
          break;
        case "/child":
          endpoint = INDEXED_CHILD;
          Parameters parameters = new Parameters();
          parameters.setQuery(request.getQueryStringDC());
          parameters.setQueryStringEncoding(StandardCharsets.UTF_8);
          parameters.handleQueryParameters();
          closure =
              new Runnable() {
                @Override
                public void run() {
                  INDEXED_CHILD.collectSpanAttributes(parameters::getParameter);
                }
              };
          break;
        default:
          endpoint = NOT_FOUND;
          break;
      }

      int status = endpoint.getStatus();
      String responseBody = endpoint == REDIRECT ? "" : endpoint.getBody();

      byte[] responseBodyBytes = responseBody.getBytes(defaultCharset());
      return new ResponseParameters(endpoint, status, responseBodyBytes, headers, closure);
    }
  }

  private static class ResponseParameters {
    private final Map<String, String> headers;
    private final ServerEndpoint endpoint;
    private final int status;
    private final byte[] responseBody;
    private final Runnable closure;

    ResponseParameters(
        ServerEndpoint endpoint,
        int status,
        byte[] responseBody,
        Map<String, String> headers,
        Runnable closure) {
      this.endpoint = endpoint;
      this.status = status;
      this.responseBody = responseBody;
      this.headers = headers;
      this.closure = closure;
    }

    int getStatus() {
      return status;
    }

    byte[] getResponseBody() {
      return responseBody;
    }

    ServerEndpoint getEndpoint() {
      return endpoint;
    }

    void fillHeaders(HttpResponsePacket.Builder builder) {
      for (Map.Entry<String, String> header : headers.entrySet()) {
        builder.header(header.getKey(), header.getValue());
      }
    }

    void execute() {
      if (closure != null) {
        closure.run();
      }
    }
  }
}
