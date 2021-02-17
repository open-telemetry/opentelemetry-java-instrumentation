/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.ws.rs.client.AsyncInvoker
import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.client.InvocationCallback
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import org.apache.cxf.jaxrs.client.spec.ClientBuilderImpl
import org.glassfish.jersey.client.ClientConfig
import org.glassfish.jersey.client.ClientProperties
import org.glassfish.jersey.client.JerseyClientBuilder
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import spock.lang.Timeout

abstract class JaxRsClientAsyncTest extends HttpClientTest implements AgentTestTrait {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    Client client = builder().build()
    WebTarget service = client.target(uri)
    def builder = service.request(MediaType.TEXT_PLAIN)
    headers.each { builder.header(it.key, it.value) }
    AsyncInvoker request = builder.async()

    def body = BODY_METHODS.contains(method) ? Entity.text("") : null
    def latch = new CountDownLatch(1)
    Response response = request.method(method, (Entity) body, new InvocationCallback<Response>() {
      @Override
      void completed(Response s) {
        callback?.call()
        latch.countDown()
      }

      @Override
      void failed(Throwable throwable) {
        latch.countDown()
      }
    }).get()
    response.close()

    // need to wait for callback to complete in case test is expecting span from it
    latch.await()
    return response.status
  }

  abstract ClientBuilder builder()
}

@Timeout(5)
class JerseyClientAsyncTest extends JaxRsClientAsyncTest {

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
class ResteasyClientAsyncTest extends JaxRsClientAsyncTest {

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
class CxfClientAsyncTest extends JaxRsClientAsyncTest {

  @Override
  ClientBuilder builder() {
    return new ClientBuilderImpl()
      .property("http.connection.timeout", (long) CONNECT_TIMEOUT_MS)
  }

  boolean testRedirects() {
    false
  }
}
