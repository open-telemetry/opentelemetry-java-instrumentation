/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import akka.actor.ActorSystem
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpMethods
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection
import spock.lang.Shared

import java.util.concurrent.TimeUnit

class AkkaHttpClientInstrumentationTest extends HttpClientTest<HttpRequest> implements AgentTestTrait {

  @Shared
  ActorSystem system = ActorSystem.create()
  @Shared
  ActorMaterializer materializer = ActorMaterializer.create(system)

  @Override
  HttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    return HttpRequest.create(uri.toString())
      .withMethod(HttpMethods.lookup(method).get())
      .addHeaders(headers.collect { RawHeader.create(it.key, it.value) })
  }

  @Override
  int sendRequest(HttpRequest request, String method, URI uri, Map<String, String> headers) {
    HttpResponse response = Http.get(system)
      .singleRequest(request, materializer)
      .toCompletableFuture()
      .get(10, TimeUnit.SECONDS)

    response.discardEntityBytes(materializer)

    return response.status().intValue()
  }

  @Override
  void sendRequestWithCallback(HttpRequest request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    Http.get(system).singleRequest(request, materializer).whenComplete { response, throwable ->
      if (throwable == null) {
        response.discardEntityBytes(materializer)
      }
      requestResult.complete({ response.status().intValue() }, throwable)
    }
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    // singleConnection test would require instrumentation to support requests made through pools
    // (newHostConnectionPool, superPool, etc), which is currently not supported.
    return null
  }

}
