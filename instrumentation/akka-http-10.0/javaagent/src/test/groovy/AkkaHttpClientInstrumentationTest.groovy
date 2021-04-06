/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT

import akka.actor.ActorSystem
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpMethods
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.CompletionStage
import java.util.function.Consumer
import spock.lang.Shared

class AkkaHttpClientInstrumentationTest extends HttpClientTest implements AgentTestTrait {

  @Shared
  ActorSystem system = ActorSystem.create()
  @Shared
  ActorMaterializer materializer = ActorMaterializer.create(system)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers) {
    return sendRequest(method, uri, headers).toCompletableFuture().get().status().intValue()
  }

  @Override
  void doRequestWithCallback(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    sendRequest(method, uri, headers).thenAccept {
      callback.accept(it.status().intValue())
    }
  }

  private CompletionStage<HttpResponse> sendRequest(String method, URI uri, Map<String, String> headers) {
    def request = HttpRequest.create(uri.toString())
      .withMethod(HttpMethods.lookup(method).get())
      .addHeaders(headers.collect { RawHeader.create(it.key, it.value) })

    return Http.get(system)
      .singleRequest(request, materializer)
  }

  // TODO(anuraaga): Context leak seems to prevent us from running asynchronous tests in a row.
  // Disable for now.
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2639
  @Override
  boolean testCallback() {
    false
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
