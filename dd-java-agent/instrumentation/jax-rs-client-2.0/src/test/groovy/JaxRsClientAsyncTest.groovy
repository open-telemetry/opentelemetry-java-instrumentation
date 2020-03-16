import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.jaxrs.JaxRsClientDecorator
import org.apache.cxf.jaxrs.client.spec.ClientBuilderImpl
import org.glassfish.jersey.client.ClientConfig
import org.glassfish.jersey.client.ClientProperties
import org.glassfish.jersey.client.JerseyClientBuilder
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import spock.lang.Timeout

import javax.ws.rs.client.AsyncInvoker
import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.client.InvocationCallback
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import java.util.concurrent.TimeUnit

abstract class JaxRsClientAsyncTest extends HttpClientTest {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    Client client = builder().build()
    WebTarget service = client.target(uri)
    def builder = service.request(MediaType.TEXT_PLAIN)
    headers.each { builder.header(it.key, it.value) }
    AsyncInvoker request = builder.async()

    def body = BODY_METHODS.contains(method) ? Entity.text("") : null
    Response response = request.method(method, (Entity) body, new InvocationCallback<Response>() {
      @Override
      void completed(Response s) {
        callback?.call()
      }

      @Override
      void failed(Throwable throwable) {
      }
    }).get()

    return response.status
  }

  @Override
  String component() {
    return JaxRsClientDecorator.DECORATE.component()
  }

  @Override
  String expectedOperationName() {
    return "jax-rs.client.call"
  }

  abstract ClientBuilder builder()
}

@Timeout(5)
class JerseyClientAsyncTest extends JaxRsClientAsyncTest {

  @Override
  ClientBuilder builder() {
    ClientConfig config = new ClientConfig()
    config.property(ClientProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT_MS)
    config.property(ClientProperties.READ_TIMEOUT, READ_TIMEOUT_MS)
    return new JerseyClientBuilder().withConfig(config)
  }

  boolean testCircularRedirects() {
    false
  }
}

@Timeout(5)
class ResteasyClientAsyncTest extends JaxRsClientAsyncTest {

  @Override
  ClientBuilder builder() {
    return new ResteasyClientBuilder()
      .establishConnectionTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .socketTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
  }

  boolean testRedirects() {
    false
  }
}

@Timeout(5)
class CxfClientAsyncTest extends JaxRsClientAsyncTest {

  @Override
  ClientBuilder builder() {
    return new ClientBuilderImpl()
  }

  boolean testRedirects() {
    false
  }

  boolean testConnectionFailure() {
    false
  }

  boolean testRemoteConnection() {
    // FIXME: span not reported correctly.
    false
  }
}
