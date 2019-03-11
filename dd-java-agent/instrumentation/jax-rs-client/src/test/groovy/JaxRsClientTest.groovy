import datadog.trace.agent.test.AgentTestRunner
import io.opentracing.tag.Tags
import org.apache.cxf.jaxrs.client.spec.ClientBuilderImpl
import org.glassfish.jersey.client.JerseyClientBuilder
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import spock.lang.AutoCleanup
import spock.lang.Shared

import javax.ws.rs.ProcessingException
import javax.ws.rs.client.AsyncInvoker
import javax.ws.rs.client.Client
import javax.ws.rs.client.Invocation
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import java.util.concurrent.ExecutionException

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.agent.test.utils.PortUtils.UNUSABLE_PORT

class JaxRsClientTest extends AgentTestRunner {

  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      all {
        response.status(200).send("pong")
      }
    }
  }

  def "#lib request creates spans and sends headers"() {
    setup:
    Client client = builder.build()
    WebTarget service = client.target("$server.address/ping")
    Response response
    if (async) {
      AsyncInvoker request = service.request(MediaType.TEXT_PLAIN).async()
      response = request.get().get()
    } else {
      Invocation.Builder request = service.request(MediaType.TEXT_PLAIN)
      response = request.get()
    }

    expect:
    response.readEntity(String) == "pong"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          resourceName "GET /ping"
          operationName "jax-rs.client.call"
          spanType "http"
          parent()
          errored false
          tags {
            "$Tags.COMPONENT.key" "jax-rs.client"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "$server.address/ping"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }

    server.lastRequest.headers.get("x-datadog-trace-id") == TEST_WRITER[0][0].traceId
    server.lastRequest.headers.get("x-datadog-parent-id") == TEST_WRITER[0][0].spanId

    where:
    builder                     | async | lib
    new JerseyClientBuilder()   | false | "jersey"
    new ClientBuilderImpl()     | false | "cxf"
    new ResteasyClientBuilder() | false | "resteasy"
    new JerseyClientBuilder()   | true  | "jersey async"
    new ClientBuilderImpl()     | true  | "cxf async"
    new ResteasyClientBuilder() | true  | "resteasy async"
  }

  def "#lib connection failure creates errored span"() {
    when:
    Client client = builder.build()
    WebTarget service = client.target("http://localhost:$UNUSABLE_PORT/ping")
    if (async) {
      AsyncInvoker request = service.request(MediaType.TEXT_PLAIN).async()
      request.get().get()
    } else {
      Invocation.Builder request = service.request(MediaType.TEXT_PLAIN)
      request.get()
    }

    then:
    thrown async ? ExecutionException : ProcessingException

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          resourceName "GET /ping"
          operationName "jax-rs.client.call"
          spanType "http"
          parent()
          errored true
          tags {
            "$Tags.COMPONENT.key" "jax-rs.client"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_URL.key" "http://localhost:$UNUSABLE_PORT/ping"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" UNUSABLE_PORT
            errorTags ProcessingException, String
            defaultTags()
          }
        }
      }
    }

    where:
    builder                     | async | lib
    new JerseyClientBuilder()   | false | "jersey"
    new ResteasyClientBuilder() | false | "resteasy"
    new JerseyClientBuilder()   | true  | "jersey async"
    new ResteasyClientBuilder() | true  | "resteasy async"
    // Unfortunately there's not a good way to instrument this for CXF.
  }
}
