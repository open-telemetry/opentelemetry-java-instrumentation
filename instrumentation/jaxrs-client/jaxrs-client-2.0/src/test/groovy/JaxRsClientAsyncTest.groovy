/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.auto.instrumentation.jaxrsclient.v2_0.JaxRsClientDecorator
import io.opentelemetry.auto.test.base.HttpClientTest
import org.apache.cxf.jaxrs.client.spec.ClientBuilderImpl
import org.glassfish.jersey.client.JerseyClientBuilder
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder

import javax.ws.rs.client.AsyncInvoker
import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.client.InvocationCallback
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import java.util.concurrent.CountDownLatch

abstract class JaxRsClientAsyncTest extends HttpClientTest {

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

    // need to wait for callback to complete in case test is expecting span from it
    latch.await()
    return response.status
  }

  @Override
  String component() {
    return JaxRsClientDecorator.DECORATE.getComponentName()
  }

  abstract ClientBuilder builder()
}

class JerseyClientAsyncTest extends JaxRsClientAsyncTest {

  @Override
  ClientBuilder builder() {
    return new JerseyClientBuilder()
  }

  boolean testCircularRedirects() {
    false
  }
}

class ResteasyClientAsyncTest extends JaxRsClientAsyncTest {

  @Override
  ClientBuilder builder() {
    return new ResteasyClientBuilder()
  }

  boolean testRedirects() {
    false
  }
}

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
}
