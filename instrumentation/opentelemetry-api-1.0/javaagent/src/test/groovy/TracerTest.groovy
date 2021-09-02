/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.api.trace.StatusCode.ERROR

class TracerTest extends AgentInstrumentationSpecification {

  def "capture span, kind, attributes, and status"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").setSpanKind(PRODUCER).startSpan()
    testSpan.setAttribute("string", "1")
    testSpan.setAttribute("long", 2)
    testSpan.setAttribute("double", 3.0)
    testSpan.setAttribute("boolean", true)
    testSpan.setStatus(ERROR)
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "test"
          kind PRODUCER
          hasNoParent()
          status ERROR
          attributes {
            "string" "1"
            "long" 2
            "double" 3.0
            "boolean" true
          }
        }
      }
    }
  }

  def "capture span with implicit parent using Tracer.withSpan()"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    Span parentSpan = tracer.spanBuilder("parent").startSpan()
    Scope parentScope = Context.current().with(parentSpan).makeCurrent()

    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.end()

    parentSpan.end()
    parentScope.close()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "test"
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "capture span with implicit parent using makeCurrent"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    Span parentSpan = tracer.spanBuilder("parent").startSpan()
    Scope parentScope = parentSpan.makeCurrent()

    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.end()

    parentSpan.end()
    parentScope.close()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "test"
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "capture span with implicit parent using TracingContextUtils.withSpan and makeCurrent"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    Span parentSpan = tracer.spanBuilder("parent").startSpan()
    def parentContext = Context.current().with(parentSpan)
    Scope parentScope = parentContext.makeCurrent()

    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.end()

    parentSpan.end()
    parentScope.close()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "test"
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "capture span with explicit parent"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    def parentSpan = tracer.spanBuilder("parent").startSpan()
    def context = Context.root().with(parentSpan)
    def testSpan = tracer.spanBuilder("test").setParent(context).startSpan()
    testSpan.end()
    parentSpan.end()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "test"
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "capture span with explicit no parent"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    def parentSpan = tracer.spanBuilder("parent").startSpan()
    def parentScope = parentSpan.makeCurrent()
    def testSpan = tracer.spanBuilder("test").setNoParent().startSpan()
    testSpan.end()
    parentSpan.end()
    parentScope.close()

    then:
    assertTraces(2) {
      traces.sort(orderByRootSpanName("parent", "test"))
      trace(0, 1) {
        span(0) {
          name "parent"
          hasNoParent()
          attributes {
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "test"
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "capture name update"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.updateName("test2")
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "test2"
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "capture exception()"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.recordException(new IllegalStateException())
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "test"
          event(0) {
            eventName("exception")
            attributes {
              "${SemanticAttributes.EXCEPTION_TYPE.key}" "java.lang.IllegalStateException"
              "${SemanticAttributes.EXCEPTION_STACKTRACE.key}" String
            }
          }
          attributes {
          }
        }
      }
    }
  }

  def "capture exception with Attributes()"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.recordException(
      new IllegalStateException(),
      Attributes.builder().put("dog", "bark").build())
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "test"
          event(0) {
            eventName("exception")
            attributes {
              "${SemanticAttributes.EXCEPTION_TYPE.key}" "java.lang.IllegalStateException"
              "${SemanticAttributes.EXCEPTION_STACKTRACE.key}" String
              "dog" "bark"
            }
          }
          attributes {
          }
        }
      }
    }
  }

  def "capture name update using TracingContextUtils.getCurrentSpan()"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def testScope = Context.current().with(testSpan).makeCurrent()
    Span.current().updateName("test2")
    testScope.close()
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "test2"
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "capture name update using TracingContextUtils.Span.fromContext(Context.current())"() {
    when:
    def tracer = GlobalOpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def testScope = Context.current().with(testSpan).makeCurrent()
    Span.fromContext(Context.current()).updateName("test2")
    testScope.close()
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "test2"
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "add wrapped span to context"() {
    when:
    // Lazy way to get a span context
    def tracer = GlobalOpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").setSpanKind(PRODUCER).startSpan()
    testSpan.end()

    def span = Span.wrap(testSpan.getSpanContext())
    def context = Context.current().with(span)

    then:
    Span.fromContext(context).getSpanContext().getSpanId() == span.getSpanContext().getSpanId()
  }
}
