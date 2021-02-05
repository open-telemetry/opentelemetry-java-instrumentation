/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static Resource.Test1
import static Resource.Test2
import static Resource.Test3

import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.apache.cxf.endpoint.Server
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean

class CxfFilterTest extends JaxRsFilterTest implements HttpServerTestTrait<Server> {

  @Override
  boolean testAbortPrematch() {
    false
  }

  @Override
  boolean runsOnServer() {
    true
  }

  @Override
  Server startServer(int port) {
    JAXRSServerFactoryBean serverFactory = new JAXRSServerFactoryBean()
    serverFactory.setProviders([simpleRequestFilter, prematchRequestFilter])
    serverFactory.setResourceClasses([Test1, Test2, Test3])
    serverFactory.setAddress(buildAddress().toString())

    def server = serverFactory.create()
    server.start()

    return server
  }

  @Override
  void stopServer(Server httpServer) {
    httpServer.stop()
  }

  Request.Builder request(HttpUrl url, String method, RequestBody body) {
    return new Request.Builder()
      .url(url)
      .method(method, body)
  }

  @Override
  def makeRequest(String path) {
    def url = HttpUrl.get(address.resolve(path)).newBuilder().build()
    def request = request(url, "POST", new FormBody.Builder().build()).build()
    Response response = client.newCall(request).execute()

    return [response.body().string(), response.code()]
  }
}