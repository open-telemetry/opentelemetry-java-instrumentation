/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.client.Invocation
import javax.ws.rs.client.InvocationCallback
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import org.apache.cxf.jaxrs.client.spec.ClientBuilderImpl
import org.glassfish.jersey.client.ClientConfig
import org.glassfish.jersey.client.ClientProperties
import org.glassfish.jersey.client.JerseyClientBuilder
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import spock.lang.Unroll

abstract class JaxRsClientTest extends HttpClientTest<Invocation.Builder> implements AgentTestTrait {

  @Override
  Invocation.Builder buildRequest(String method, URI uri, Map<String, String> headers) {
    return internalBuildRequest(uri, headers)
  }

  @Override
  int sendRequest(Invocation.Builder request, String method, URI uri, Map<String, String> headers) {
    def body = BODY_METHODS.contains(method) ? Entity.text("") : null
    def response = request.build(method, body).invoke()
    response.close()
    return response.status
  }

  @Override
  void sendRequestWithCallback(Invocation.Builder request, String method, URI uri, Map<String, String> headers, Consumer<Integer> callback) {
    def body = BODY_METHODS.contains(method) ? Entity.text("") : null

    request.async().method(method, (Entity) body, new InvocationCallback<Response>() {
      @Override
      void completed(Response response) {
        callback.accept(response.status)
      }

      @Override
      void failed(Throwable throwable) {
        throw throwable
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
    def uri = server.address.resolve(path)

    when:
    def actualStatusCode = doRequest(method, uri)

    then:
    assert actualStatusCode == statusCode

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          hasNoParent()
          name expectedOperationName(method)
          kind CLIENT
          errored true
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_NAME.key}" uri.host
            "${SemanticAttributes.NET_PEER_IP.key}" { it == null || it == "127.0.0.1" }
            "${SemanticAttributes.NET_PEER_PORT.key}" uri.port > 0 ? uri.port : { it == null || it == 443 }
            "${SemanticAttributes.HTTP_URL.key}" "${uri}"
            "${SemanticAttributes.HTTP_METHOD.key}" method
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" statusCode
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
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
  boolean testCircularRedirects() {
    false
  }

  // TODO jaxrs client instrumentation captures the (default) user-agent on the second (reused)
  //  request, which then fails the test verification
  //  ideally the instrumentation would capture the default user-agent on the first request,
  //  and the test http server would verify that the user-agent was sent and matches what was
  //  captured from the client instrumentation
  @Override
  boolean testReusedRequest() {
    false
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
}
