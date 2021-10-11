/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import org.glassfish.grizzly.filterchain.BaseFilter
import org.glassfish.grizzly.filterchain.FilterChain
import org.glassfish.grizzly.filterchain.FilterChainBuilder
import org.glassfish.grizzly.filterchain.FilterChainContext
import org.glassfish.grizzly.filterchain.NextAction
import org.glassfish.grizzly.filterchain.TransportFilter
import org.glassfish.grizzly.http.HttpContent
import org.glassfish.grizzly.http.HttpHeader
import org.glassfish.grizzly.http.HttpRequestPacket
import org.glassfish.grizzly.http.HttpResponsePacket
import org.glassfish.grizzly.http.HttpServerFilter
import org.glassfish.grizzly.http.server.HttpServer
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection
import org.glassfish.grizzly.nio.transport.TCPNIOTransport
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder
import org.glassfish.grizzly.utils.DelayedExecutor
import org.glassfish.grizzly.utils.IdleTimeoutFilter

import java.util.concurrent.Executors

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.AUTH_REQUIRED
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static java.lang.String.valueOf
import static java.nio.charset.Charset.defaultCharset
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static org.glassfish.grizzly.memory.Buffers.wrap

class GrizzlyFilterchainServerTest extends HttpServerTest<HttpServer> implements AgentTestTrait {

  private TCPNIOTransport transport
  private TCPNIOServerConnection serverConnection

  @Override
  HttpServer startServer(int port) {
    FilterChain filterChain = setUpFilterChain()
    setUpTransport(filterChain)

    serverConnection = transport.bind("127.0.0.1", port)
    transport.start()
    return null
  }

  @Override
  void stopServer(HttpServer httpServer) {
    transport.shutdownNow()
  }

  @Override
  boolean testException() {
    // justification: grizzly async closes the channel which
    // looks like a ConnectException to the client when this happens
    false
  }

  @Override
  boolean testCapturedHttpHeaders() {
    false
  }

  @Override
  boolean verifyServerSpanEndTime() {
    // SERVER span is ended inside of the controller span below
    return false
  }

  void setUpTransport(FilterChain filterChain) {
    TCPNIOTransportBuilder transportBuilder = TCPNIOTransportBuilder.newInstance()
      .setOptimizedForMultiplexing(true)

    transportBuilder.setTcpNoDelay(true)
    transportBuilder.setKeepAlive(false)
    transportBuilder.setReuseAddress(true)
    transportBuilder.setServerConnectionBackLog(50)
    transportBuilder.setServerSocketSoTimeout(80000)

    transport = transportBuilder.build()
    transport.setProcessor(filterChain)
  }

  FilterChain setUpFilterChain() {
    return FilterChainBuilder.stateless()
      .add(createTransportFilter())
      .add(createIdleTimeoutFilter())
      .add(new HttpServerFilter())
      .add(new LastFilter())
      .build()
  }

  TransportFilter createTransportFilter() {
    return new TransportFilter()
  }

  IdleTimeoutFilter createIdleTimeoutFilter() {
    return new IdleTimeoutFilter(new DelayedExecutor(Executors.newCachedThreadPool()), 80000, MILLISECONDS)
  }

  static class LastFilter extends BaseFilter {

    @Override
    NextAction handleRead(final FilterChainContext ctx) throws IOException {
      if (ctx.getMessage() instanceof HttpContent) {
        HttpContent httpContent = ctx.getMessage()
        HttpHeader httpHeader = httpContent.getHttpHeader()
        if (httpHeader instanceof HttpRequestPacket) {
          HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader()
          ResponseParameters responseParameters = buildResponse(request)
          HttpResponsePacket.Builder builder = HttpResponsePacket.builder(request)
            .status(responseParameters.getStatus())
            .header("Content-Length", valueOf(responseParameters.getResponseBody().length))
          responseParameters.fillHeaders(builder)
          HttpResponsePacket responsePacket = builder.build()
          controller(responseParameters.getEndpoint()) {
            ctx.write(HttpContent.builder(responsePacket)
              .content(wrap(ctx.getMemoryManager(), responseParameters.getResponseBody()))
              .build())
          }
        }
      }
      return ctx.getStopAction()
    }

    ResponseParameters buildResponse(HttpRequestPacket request) {
      String uri = request.getRequestURI()
      String requestParams = request.getQueryString()
      String fullPath = uri + (requestParams != null ? "?" + requestParams : "")

      Map<String, String> headers = new HashMap<>()

      HttpServerTest.ServerEndpoint endpoint
      switch (fullPath) {
        case "/success":
          endpoint = SUCCESS
          break
        case "/redirect":
          endpoint = REDIRECT
          headers.put("location", REDIRECT.body)
          break
        case "/error-status":
          endpoint = ERROR
          break
        case "/exception":
          throw new Exception(EXCEPTION.body)
        case "/query?some=query":
          endpoint = QUERY_PARAM
          break
        case "/path/123/param":
          endpoint = PATH_PARAM
          break
        case "/authRequired":
          endpoint = AUTH_REQUIRED
          break
        default:
          endpoint = NOT_FOUND
          break
      }

      int status = endpoint.status
      String responseBody = endpoint == REDIRECT ? "" : endpoint.body

      byte[] responseBodyBytes = responseBody.getBytes(defaultCharset())
      return new ResponseParameters(endpoint, status, responseBodyBytes, headers)
    }

    static class ResponseParameters {
      Map<String, String> headers
      HttpServerTest.ServerEndpoint endpoint
      int status
      byte[] responseBody

      ResponseParameters(HttpServerTest.ServerEndpoint endpoint,
                         int status,
                         byte[] responseBody,
                         Map<String, String> headers) {
        this.endpoint = endpoint
        this.status = status
        this.responseBody = responseBody
        this.headers = headers
      }

      int getStatus() {
        return status
      }

      byte[] getResponseBody() {
        return responseBody
      }

      HttpServerTest.ServerEndpoint getEndpoint() {
        return endpoint
      }

      void fillHeaders(HttpResponsePacket.Builder builder) {
        for (Map.Entry<String, String> header : headers.entrySet()) {
          builder.header(header.getKey(), header.getValue())
        }
      }
    }
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    return "HttpCodecFilter.handleRead"
  }
}
