/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.TimeUnit
import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.client.Invocation
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import org.apache.cxf.jaxrs.client.spec.ClientBuilderImpl
import org.glassfish.jersey.client.ClientConfig
import org.glassfish.jersey.client.ClientProperties
import org.glassfish.jersey.client.JerseyClientBuilder
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import spock.lang.Timeout
import spock.lang.Unroll

abstract class JaxRsClientTest extends HttpClientTest implements AgentTestTrait {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {

    Client client = builder().build()
    WebTarget service = client.target(uri)
    Invocation.Builder request = service.request(MediaType.TEXT_PLAIN)
    headers.each { request.header(it.key, it.value) }
    def body = BODY_METHODS.contains(method) ? Entity.text("") : null
    Response response = request.method(method, (Entity) body)
    callback?.call()

    return response.status
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

@Timeout(5)
class JerseyClientTest extends JaxRsClientTest {

  @Override
  ClientBuilder builder() {
    ClientConfig config = new ClientConfig()
    config.property(ClientProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT_MS)
    return new JerseyClientBuilder().withConfig(config)
  }

  boolean testCircularRedirects() {
    false
  }
}

@Timeout(5)
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

@Timeout(5)
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
