/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.extension.annotations.WithSpan
import io.opentelemetry.instrumentation.test.AgentTestRunner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class ContextBridgeTest extends AgentTestRunner {

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

  def "application propagates agent's span"() {
    when:
    new Runnable() {
      @WithSpan("test")
      @Override
      void run() {
        // using @WithSpan above to make the agent generate a span
        // and then using manual propagation below to verify that span can be propagated by user
        def context = Context.current()
        Context.root().makeCurrent().withCloseable {
          Span.current().setAttribute("dog", "yes")
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
    def tracer = OpenTelemetry.getGlobalTracer("test")

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
}
