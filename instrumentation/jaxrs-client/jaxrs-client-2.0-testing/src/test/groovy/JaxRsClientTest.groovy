/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection
import io.opentelemetry.semconv.ServerAttributes
import io.opentelemetry.semconv.ErrorAttributes
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.NetworkAttributes
import io.opentelemetry.semconv.UrlAttributes
import org.apache.cxf.jaxrs.client.spec.ClientBuilderImpl
import org.glassfish.jersey.client.ClientConfig
import org.glassfish.jersey.client.ClientProperties
import org.glassfish.jersey.client.JerseyClientBuilder
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import spock.lang.Unroll

import javax.ws.rs.ProcessingException
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.client.Invocation
import javax.ws.rs.client.InvocationCallback
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.StatusCode.ERROR

abstract class JaxRsClientTest extends HttpClientTest<Invocation.Builder> implements AgentTestTrait {

  boolean testRedirects() {
    false
  }

  @Override
  boolean testNonStandardHttpMethod() {
    false
  }

  @Override
  Invocation.Builder buildRequest(String method, URI uri, Map<String, String> headers) {
    return internalBuildRequest(uri, headers)
  }

  @Override
  int sendRequest(Invocation.Builder request, String method, URI uri, Map<String, String> headers) {
    try {
      def body = BODY_METHODS.contains(method) ? Entity.text("") : null
      def response = request.build(method, body).invoke()
      // read response body to avoid broken pipe errors on the server side
      response.readEntity(String)
      try {
        response.close()
      } catch (IOException ignore) {
      }
      return response.status
    } catch (ProcessingException exception) {
      throw exception.getCause()
    }
  }

  @Override
  void sendRequestWithCallback(Invocation.Builder request, String method, URI uri, Map<String, String> headers, HttpClientResult requestResult) {
    def body = BODY_METHODS.contains(method) ? Entity.text("") : null

    request.async().method(method, (Entity) body, new InvocationCallback<Response>() {
      @Override
      void completed(Response response) {
        // read response body
        response.readEntity(String)
        requestResult.complete(response.status)
      }

      @Override
      void failed(Throwable throwable) {
        if (throwable instanceof ProcessingException) {
          throwable = throwable.getCause()
        }
        requestResult.complete(throwable)
      }
    })
  }

  private Invocation.Builder internalBuildRequest(URI uri, Map<String, String> headers) {
    def client = builder().build()
    def service = client.target(uri)
    def requestBuilder = service.request(MediaType.TEXT_PLAIN)
    headers.each { requestBuilder.header(it.key, it.value) }
    return requestBuilder
  }

  abstract ClientBuilder builder()

  @Unroll
  def "should properly convert HTTP status #statusCode to span error status"() {
    given:
    def method = "GET"
    def uri = resolveAddress(path)

    when:
    def actualStatusCode = doRequest(method, uri)

    then:
    assert actualStatusCode == statusCode

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          hasNoParent()
          name "$method"
          kind CLIENT
          status ERROR
          attributes {
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_ADDRESS" uri.host
            "$ServerAttributes.SERVER_PORT" uri.port > 0 ? uri.port : { it == null || it == 443 }
            "$NetworkAttributes.NETWORK_PEER_ADDRESS" { it == "127.0.0.1" || it == null }
            "$UrlAttributes.URL_FULL" "${uri}"
            "$HttpAttributes.HTTP_REQUEST_METHOD" method
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" statusCode
            "$ErrorAttributes.ERROR_TYPE" "$statusCode"
          }
        }
        serverSpan(it, 1, span(0))
      }
    }

    where:
    path            | statusCode
    "/client-error" | 400
    "/error"        | 500
  }
}

class JerseyClientTest extends JaxRsClientTest {

  @Override
  ClientBuilder builder() {
    ClientConfig config = new ClientConfig()
    config.property(ClientProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT_MS)
    config.property(ClientProperties.READ_TIMEOUT, READ_TIMEOUT_MS)
    return new JerseyClientBuilder().withConfig(config)
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    // Jersey JAX-RS client uses HttpURLConnection internally, which does not support pipelining nor
    // waiting for a connection in the pool to become available. Therefore a high concurrency test
    // would require manually doing requests one after another which is not meaningful for a high
    // concurrency test.
    return null
  }
}

class ResteasyClientTest extends JaxRsClientTest {

  @Override
  ClientBuilder builder() {
    return new ResteasyClientBuilder()
      .establishConnectionTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .socketTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    return new ResteasySingleConnection(host, port)
  }
}

class CxfClientTest extends JaxRsClientTest {

  @Override
  Throwable clientSpanError(URI uri, Throwable exception) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
        if (exception.getCause() instanceof ConnectException) {
          exception = exception.getCause()
        }
        break
      case "https://192.0.2.1/": // non routable address
        if (exception.getCause() != null) {
          exception = exception.getCause()
        }
    }
    return exception
  }

  @Override
  boolean testWithClientParent() {
    !Boolean.getBoolean("testLatestDeps")
  }

  @Override
  boolean testReadTimeout() {
    return false
  }

  @Override
  ClientBuilder builder() {
    return new ClientBuilderImpl()
      .property("http.connection.timeout", (long) CONNECT_TIMEOUT_MS)
      .property("org.apache.cxf.transport.http.forceVersion", "1.1")
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    // CXF JAX-RS client uses HttpURLConnection internally, which does not support pipelining nor
    // waiting for a connection in the pool to become available. Therefore a high concurrency test
    // would require manually doing requests one after another which is not meaningful for a high
    // concurrency test.
    return null
  }
}
