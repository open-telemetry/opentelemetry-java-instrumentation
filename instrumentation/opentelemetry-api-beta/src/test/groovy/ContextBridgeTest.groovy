/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import application.io.opentelemetry.OpenTelemetry
import application.io.opentelemetry.context.Context
import application.io.opentelemetry.context.ContextKey
import application.io.opentelemetry.trace.Span
import io.opentelemetry.instrumentation.test.AgentTestRunner
import java.util.concurrent.atomic.AtomicReference

class ContextBridgeTest extends AgentTestRunner {

  private static final ContextKey<String> ANIMAL = ContextKey.named("animal")

  private static final io.opentelemetry.context.ContextKey<String> FOOD =
    io.opentelemetry.context.ContextKey.named("food")
  private static final io.opentelemetry.context.ContextKey<String> COUNTRY =
    io.opentelemetry.context.ContextKey.named("country")

  def "agent and application mix"() {
    expect:
    def agentContext = io.opentelemetry.context.Context.current().with(COUNTRY, "japan")
    io.opentelemetry.context.Context.current().get(COUNTRY) == null
    agentContext.makeCurrent().withCloseable {
      io.opentelemetry.context.Context.current().get(COUNTRY) == "japan"
      Context.current().with(ANIMAL, "cat").makeCurrent().withCloseable {
        Context.current().get(ANIMAL) == "cat"
        io.opentelemetry.context.Context.current().get(COUNTRY) == "japan"

        def agentContext2 = io.opentelemetry.context.Context.current().with(FOOD, "cheese")
        io.opentelemetry.context.Context.current().get(FOOD) == null
        agentContext2.makeCurrent().withCloseable {
          io.opentelemetry.context.Context.current().get(FOOD) == "cheese"
          io.opentelemetry.context.Context.current().get(COUNTRY) == "japan"
          Context.current().get(ANIMAL) == "cat"
        }
      }
    }
  }

  // The difference between "standard" context interop and our bridge is that with normal interop,
  // keys are still isolated completely. We have special logic to share the same data for our known
  // types like span.
  def "agent and application share span"() {
    when:
    def applicationTracer = OpenTelemetry.getGlobalTracer("test")
    def agentTracer = io.opentelemetry.OpenTelemetry.getGlobalTracer("test")

    then:
    !Span.current().spanContext.isValid()
    !io.opentelemetry.trace.Span.current().spanContext.isValid()

    def applicationSpan = applicationTracer.spanBuilder("test1").startSpan()
    applicationSpan.spanContext.isValid()
    applicationSpan.makeCurrent().withCloseable {
      Span.current().spanContext.spanIdAsHexString == applicationSpan.spanContext.spanIdAsHexString
      io.opentelemetry.trace.Span.current().spanContext.spanIdAsHexString == applicationSpan.spanContext.spanIdAsHexString

      def agentSpan = agentTracer.spanBuilder("test2").startSpan()
      agentSpan.makeCurrent().withCloseable {
        Span.current().spanContext.spanIdAsHexString == agentSpan.spanContext.spanIdAsHexString
        Span.current().spanContext.traceIdAsHexString == agentSpan.spanContext.spanIdAsHexString
        Span.current().spanContext.traceIdAsHexString == applicationSpan.spanContext.spanIdAsHexString
        io.opentelemetry.trace.Span.current().spanContext.spanIdAsHexString == agentSpan.spanContext.spanIdAsHexString
        io.opentelemetry.trace.Span.current().spanContext.traceIdAsHexString == agentSpan.spanContext.traceIdAsHexString
        io.opentelemetry.trace.Span.current().spanContext.traceIdAsHexString == applicationSpan.spanContext.traceIdAsHexString

        def applicationSpan2 = applicationTracer.spanBuilder("test3").startSpan()
        applicationSpan2.makeCurrent().withCloseable {
          Span.current().spanContext.spanIdAsHexString == applicationSpan2.spanContext.spanIdAsHexString
          Span.current().spanContext.traceIdAsHexString == applicationSpan2.spanContext.spanIdAsHexString
          Span.current().spanContext.traceIdAsHexString == applicationSpan.spanContext.spanIdAsHexString
          io.opentelemetry.trace.Span.current().spanContext.spanIdAsHexString == applicationSpan2.spanContext.spanIdAsHexString
          io.opentelemetry.trace.Span.current().spanContext.traceIdAsHexString == applicationSpan2.spanContext.traceIdAsHexString
          io.opentelemetry.trace.Span.current().spanContext.traceIdAsHexString == applicationSpan.spanContext.traceIdAsHexString
        }
      }
    }
  }

  def "agent wraps"() {
    expect:
    def agentContext = io.opentelemetry.context.Context.current().with(COUNTRY, "japan")
    agentContext.makeCurrent().withCloseable {
      Context.current().with(ANIMAL, "cat").makeCurrent().withCloseable {
        io.opentelemetry.context.Context.current().get(COUNTRY) == "japan"
        Context.current().get(ANIMAL) == "cat"

        def agentValue = new AtomicReference<String>()
        def applicationValue = new AtomicReference<String>()
        Runnable runnable = {
          agentValue.set(io.opentelemetry.context.Context.current().get(COUNTRY))
          applicationValue.set(Context.current().get(ANIMAL))
        }

        runnable.run()
        agentValue.get() == null
        applicationValue.get() == null

        def ctx = io.opentelemetry.context.Context.current()
        // Simulate another thread by remounting root
        Context.root().makeCurrent().withCloseable {
          io.opentelemetry.context.Context.root().makeCurrent().withCloseable {
            ctx.wrap(runnable).run()
          }
        }
        agentValue.get() == "japan"
        applicationValue.get() == "cat"
      }
    }
  }

  def "application wraps"() {
    expect:
    def agentContext = io.opentelemetry.context.Context.current().with(COUNTRY, "japan")
    agentContext.makeCurrent().withCloseable {
      Context.current().with(ANIMAL, "cat").makeCurrent().withCloseable {
        io.opentelemetry.context.Context.current().get(COUNTRY) == "japan"
        Context.current().get(ANIMAL) == "cat"

        def agentValue = new AtomicReference<String>()
        def applicationValue = new AtomicReference<String>()
        Runnable runnable = {
          agentValue.set(io.opentelemetry.context.Context.current().get(COUNTRY))
          applicationValue.set(Context.current().get(ANIMAL))
        }

        agentValue.get() == null
        applicationValue.get() == null

        def ctx = Context.current()
        // Simulate another thread by remounting root
        Context.root().makeCurrent().withCloseable {
          io.opentelemetry.context.Context.root().makeCurrent().withCloseable {
            ctx.wrap(runnable).run()
          }
        }
        agentValue.get() == "japan"
        applicationValue.get() == "cat"
      }
    }
  }
}
