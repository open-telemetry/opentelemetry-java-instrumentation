/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.extension.annotations.WithSpan
import io.opentelemetry.instrumentation.api.instrumenter.SpanKey
import io.opentelemetry.instrumentation.api.tracer.ClientSpan
import io.opentelemetry.instrumentation.api.tracer.ConsumerSpan
import io.opentelemetry.instrumentation.api.tracer.ServerSpan
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class ContextBridgeTest extends AgentInstrumentationSpecification {

  private static final ContextKey<String> ANIMAL = ContextKey.named("animal")

  def "agent propagates application's context"() {
    when:
    def context = Context.current().with(ANIMAL, "cat")
    def captured = new AtomicReference<String>()
    context.makeCurrent().withCloseable {
      Executors.newSingleThreadExecutor().submit({
        captured.set(Context.current().get(ANIMAL))
      }).get()
    }

    then:
    captured.get() == "cat"
  }

  def "application propagates agent's context"() {
    when:
    new Runnable() {
      @WithSpan("test")
      @Override
      void run() {
        // using @WithSpan above to make the agent generate a context
        // and then using manual propagation below to verify that context can be propagated by user
        def context = Context.current()
        Context.root().makeCurrent().withCloseable {
          Span.current().setAttribute("dog", "no")
          context.makeCurrent().withCloseable {
            Span.current().setAttribute("cat", "yes")
          }
        }
      }
    }.run()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "test"
          hasNoParent()
          attributes {
            "cat" "yes"
          }
        }
      }
    }
  }

  def "agent propagates application's span"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")

    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.makeCurrent().withCloseable {
      Executors.newSingleThreadExecutor().submit({
        Span.current().setAttribute("cat", "yes")
      }).get()
    }
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "test"
          hasNoParent()
          attributes {
            "cat" "yes"
          }
        }
      }
    }
  }

  def "application propagates agent's span"() {
    when:
    new Runnable() {
      @WithSpan("test")
      @Override
      void run() {
        // using @WithSpan above to make the agent generate a span
        // and then using manual propagation below to verify that span can be propagated by user
        def span = Span.current()
        Context.root().makeCurrent().withCloseable {
          Span.current().setAttribute("dog", "no")
          span.makeCurrent().withCloseable {
            Span.current().setAttribute("cat", "yes")
          }
        }
      }
    }.run()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "test"
          hasNoParent()
          attributes {
            "cat" "yes"
          }
        }
      }
    }
  }

  def "agent propagates application's baggage"() {
    when:
    def testBaggage = Baggage.builder().put("cat", "yes").build()
    def ref = new AtomicReference<Baggage>()
    def latch = new CountDownLatch(1)
    testBaggage.makeCurrent().withCloseable {
      Executors.newSingleThreadExecutor().submit({
        ref.set(Baggage.current())
        latch.countDown()
      }).get()
    }

    then:
    latch.await()
    ref.get().size() == 1
    ref.get().getEntryValue("cat") == "yes"
  }

  def "test empty current context is root context"() {
    expect:
    Context.current() == Context.root()
  }

  def "test server span bridge"() {
    expect:
    AgentSpanTesting.runWithServerSpan("server") {
      assert Span.current() != null
      assert ServerSpan.fromContextOrNull(Context.current()) != null
      runWithSpan("internal") {
        assert ServerSpan.fromContextOrNull(Context.current()) != null
      }
    }
  }

  def "test consumer span bridge"() {
    expect:
    AgentSpanTesting.runWithConsumerSpan("consumer") {
      assert Span.current() != null
      assert ConsumerSpan.fromContextOrNull(Context.current()) != null
      runWithSpan("internal") {
        assert ConsumerSpan.fromContextOrNull(Context.current()) != null
      }
    }
  }

  def "test client span bridge"() {
    expect:
    AgentSpanTesting.runWithClientSpan("client") {
      assert Span.current() != null
      assert ClientSpan.fromContextOrNull(Context.current()) != null
      runWithSpan("internal") {
        assert ClientSpan.fromContextOrNull(Context.current()) != null
      }
    }
  }

  def "test span key bridge"() {
    expect:
    AgentSpanTesting.runWithAllSpanKeys("parent") {
      assert Span.current() != null
      def spanKeys = [
        SpanKey.SERVER,
        SpanKey.CONSUMER,
        SpanKey.HTTP_CLIENT,
        SpanKey.RPC_CLIENT,
        SpanKey.DB_CLIENT,
        SpanKey.MESSAGING_PRODUCER,
        SpanKey.ALL_CLIENTS,
        SpanKey.ALL_PRODUCERS
      ]
      spanKeys.each { spanKey ->
        assert spanKey.fromContextOrNull(Context.current()) != null
      }
      runWithSpan("internal") {
        spanKeys.each { spanKey ->
          assert spanKey.fromContextOrNull(Context.current()) != null
        }
      }
    }
  }

  // TODO (trask)
  // more tests are needed here, not sure how to implement, probably need to write some test
  // instrumentation to help test, similar to :testing-common:integration-tests
  //
  // * "application propagates agent's baggage"
  // * "agent uses application's span"
  // * "application uses agent's span" (this is covered above by "application propagates agent's span")
  // * "agent uses application's baggage"
  // * "application uses agent's baggage"
}
