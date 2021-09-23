/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
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
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

abstract class JaxRsClientTest extends HttpClientTest<Invocation.Builder> implements AgentTestTrait {

  @Override
  Invocation.Builder buildRequest(String method, URI uri, Map<String, String> headers) {
    return internalBuildRequest(uri, headers)
  }

  @Override
  int sendRequest(Invocation.Builder request, String method, URI uri, Map<String, String> headers) {
    try {
      def body = BODY_METHODS.contains(method) ? Entity.text("") : null
      def response = request.build(method, body).invoke()
      response.close()
      return response.status
    } catch (ProcessingException exception) {
      throw exception.getCause()
    }
  }

  @Override
  void sendRequestWithCallback(Invocation.Builder request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    def body = BODY_METHODS.contains(method) ? Entity.text("") : null

    request.async().method(method, (Entity) body, new InvocationCallback<Response>() {
      @Override
      void completed(Response response) {
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
          name "HTTP $method"
          kind CLIENT
          status ERROR
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" uri.host
            "${SemanticAttributes.NET_PEER_IP.key}" { it == null || it == "127.0.0.1" }
            "${SemanticAttributes.NET_PEER_PORT.key}" uri.port > 0 ? uri.port : { it == null || it == 443 }
            "${SemanticAttributes.HTTP_URL.key}" "${uri}"
            "${SemanticAttributes.HTTP_METHOD.key}" method
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" statusCode
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_HOST.key}" "localhost"
            "${SemanticAttributes.HTTP_SCHEME.key}" "http"
            "${SemanticAttributes.HTTP_TARGET.key}" path
            "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH.key}" Long
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
    return new JerseyClientBuilder().withConfig(config)
  }

  @Override
  int maxRedirects() {
    20
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
  }

  boolean testRedirects() {
    false
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    return new ResteasySingleConnection(host, port)
  }
}

class CxfClientTest extends JaxRsClientTest {

  @Override
  ClientBuilder builder() {
    return new ClientBuilderImpl()
      .property("http.connection.timeout", (long) CONNECT_TIMEOUT_MS)
  }

  boolean testRedirects() {
    false
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
