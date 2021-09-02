/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import reactor.core.publisher.Mono

import java.util.concurrent.CountDownLatch

abstract class AbstractSubscriptionTest extends InstrumentationSpecification {

  def "subscription test"() {
    when:
    CountDownLatch latch = new CountDownLatch(1)
    runWithSpan("parent") {
      Mono<Connection> connection = Mono.create {
        it.success(new Connection())
      }
      connection.subscribe {
        it.query()
        latch.countDown()
      }
    }
    latch.await()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "Connection.query"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }

  }

  static class Connection {
    static int query() {
      def span = GlobalOpenTelemetry.getTracer("test").spanBuilder("Connection.query").startSpan()
      span.end()
      return new Random().nextInt()
    }
  }
}
