import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.jaxrs.JaxRsClientDecorator
import org.apache.cxf.jaxrs.client.spec.ClientBuilderImpl
import org.glassfish.jersey.client.ClientConfig
import org.glassfish.jersey.client.ClientProperties
import org.glassfish.jersey.client.JerseyClientBuilder
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import spock.lang.Timeout

import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.client.Invocation
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import java.util.concurrent.TimeUnit

abstract class JaxRsClientTest extends HttpClientTest {

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
class JerseyClientTest extends JaxRsClientTest {

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
class ResteasyClientTest extends JaxRsClientTest {

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
class CxfClientTest extends JaxRsClientTest {

  @Override
  ClientBuilder builder() {
    return new ClientBuilderImpl()
//      .property(ClientImpl.HTTP_CONNECTION_TIMEOUT_PROP, (long) CONNECT_TIMEOUT_MS)
//      .property(ClientImpl.HTTP_RECEIVE_TIMEOUT_PROP, (long) READ_TIMEOUT_MS)
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
