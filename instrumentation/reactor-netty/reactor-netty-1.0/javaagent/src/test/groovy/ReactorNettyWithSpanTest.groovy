/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer
import io.opentelemetry.test.reactor.netty.TracedWithSpan
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import reactor.test.StepVerifier
import spock.lang.Shared

import java.time.Duration

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER

class ReactorNettyWithSpanTest extends InstrumentationSpecification implements AgentTestTrait {

  @Shared
  private HttpClientTestServer server

  def setupSpec() {
    server = new HttpClientTestServer(openTelemetry)
    server.start()
  }

  def cleanupSpec() {
    server.stop()
  }

  def "test successful nested under WithSpan"() {
    when:
    def httpClient = HttpClient.create()

    def httpRequest = Mono.defer({ ->
      httpClient.get().uri("http://localhost:${server.httpPort()}/success")
        .responseSingle ({ resp, content ->
          // Make sure to consume content since that's when we close the span.
          content.map { resp }
        })
        .map({ r -> r.status().code() })
    })

    def getResponse = new TracedWithSpan().mono(
      // our HTTP server is synchronous, i.e. it returns Mono.just with response
      // which is not supported by TracingSubscriber - it does not instrument scalar calls
      // so we delay here to fake async http request and let Reactor context instrumentation work
      Mono.delay(Duration.ofMillis(1)).then(httpRequest))

    then:
    StepVerifier.create(getResponse)
      .expectNext(200)
      .expectComplete()
      .verify()

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "TracedWithSpan.mono"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "HTTP GET"
          kind CLIENT
          childOf(span(0))
        }
        span(2) {
          name "test-http-server"
          kind SERVER
          childOf(span(1))
        }
      }
    }
  }
}
