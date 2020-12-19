/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.baggage.BaggageEntryMetadata
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.extension.annotations.WithSpan
import io.opentelemetry.instrumentation.test.AgentTestRunner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import spock.lang.Ignore

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

  // FIXME (trask)
  @Ignore
  def "agent and application share baggage"() {
    expect:
    def applicationBaggage = Baggage.builder()
      .put("food", "cheese")
      .put("country", "japan", BaggageEntryMetadata.create("asia"))
      .build()

    applicationBaggage.makeCurrent().withCloseable {
      def agentBaggage = io.opentelemetry.api.baggage.Baggage.current()
      agentBaggage.asMap().with {
        size() == 2
        get("food").value == "cheese"
        get("food").entryMetadata == io.opentelemetry.api.baggage.BaggageEntryMetadata.empty()
        get("country").value == "japan"
        get("country").entryMetadata == io.opentelemetry.api.baggage.BaggageEntryMetadata.create("asia")
      }

      agentBaggage = io.opentelemetry.api.baggage.Baggage.builder()
        .put("country", "italy", io.opentelemetry.api.baggage.BaggageEntryMetadata.create("europe"))
        .build()
      agentBaggage.makeCurrent().withCloseable {
        def updatedApplicationBaggage = Baggage.current()
        updatedApplicationBaggage.asMap().with {
          size() == 2
          get("food").value == "cheese"
          get("food").entryMetadata == BaggageEntryMetadata.empty()
          get("country").value == "italy"
          get("country").entryMetadata == BaggageEntryMetadata.create("europe")
        }

        applicationBaggage = applicationBaggage.toBuilder()
          .put("food", "cabbage")
          .build()
        applicationBaggage.makeCurrent().withCloseable {
          agentBaggage = io.opentelemetry.api.baggage.Baggage.current()
          agentBaggage.asMap().with {
            size() == 2
            get("food").value == "cabbage"
            get("food").entryMetadata == io.opentelemetry.api.baggage.BaggageEntryMetadata.empty()
            get("country").value == "japan"
            get("country").entryMetadata == io.opentelemetry.api.baggage.BaggageEntryMetadata.create("asia")
          }
        }
      }
    }
  }
}
