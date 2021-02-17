/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT

import akka.actor.ActorSystem
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpMethods
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class AkkaHttpClientInstrumentationTest extends HttpClientTest implements AgentTestTrait {

  @Shared
  ActorSystem system = ActorSystem.create()
  @Shared
  ActorMaterializer materializer = ActorMaterializer.create(system)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = HttpRequest.create(uri.toString())
      .withMethod(HttpMethods.lookup(method).get())
      .addHeaders(headers.collect { RawHeader.create(it.key, it.value) })

    def response = Http.get(system)
      .singleRequest(request, materializer)
    //.whenComplete { result, error ->
    // FIXME: Callback should be here instead.
    //  callback?.call()
    //}
      .toCompletableFuture()
      .get()
    callback?.call()
    return response.status().intValue()
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    // Not sure how to properly set timeouts...
    return false
  }

  @Override
  boolean testCausality() {
    false
  }

  def "singleRequest exception trace"() {
    when:
    // Passing null causes NPE in singleRequest
    Http.get(system).singleRequest(null, materializer)

    then:
    def e = thrown NullPointerException
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          hasNoParent()
          name "HTTP request"
          kind CLIENT
          errored true
          errorEvent(NullPointerException, e.getMessage())
        }
      }
    }
  }
}
