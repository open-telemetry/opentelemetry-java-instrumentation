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
import akka.actor.ActorSystem
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpMethods
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator
import io.opentelemetry.auto.test.base.HttpClientTest
import spock.lang.Shared

import static io.opentelemetry.trace.Span.Kind.CLIENT

class AkkaHttpClientInstrumentationTest extends HttpClientTest {

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

  def "singleRequest exception trace"() {
    when:
    // Passing null causes NPE in singleRequest
    Http.get(system).singleRequest(null, materializer)

    then:
    thrown NullPointerException
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          parent()
          operationName HttpClientDecorator.DEFAULT_SPAN_NAME
          spanKind CLIENT
          errored true
          tags {
            errorTags(NullPointerException)
          }
        }
      }
    }
  }
}
