/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static application.io.opentelemetry.context.ContextUtils.withScopedContext
import static application.io.opentelemetry.trace.Span.Kind.PRODUCER
import static application.io.opentelemetry.trace.TracingContextUtils.currentContextWith
import static application.io.opentelemetry.trace.TracingContextUtils.getCurrentSpan
import static application.io.opentelemetry.trace.TracingContextUtils.getSpan
import static application.io.opentelemetry.trace.TracingContextUtils.withSpan

import application.io.grpc.Context
import application.io.opentelemetry.OpenTelemetry
import application.io.opentelemetry.common.Attributes
import application.io.opentelemetry.context.Scope
import application.io.opentelemetry.trace.DefaultSpan
import application.io.opentelemetry.trace.Span
import application.io.opentelemetry.trace.StatusCanonicalCode
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.trace.attributes.SemanticAttributes

class TracerTest extends AgentTestRunner {

  def "capture span, kind, attributes, and status"() {
    when:
    def tracer = OpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").setSpanKind(PRODUCER).startSpan()
    testSpan.setAttribute("string", "1")
    testSpan.setAttribute("long", 2)
    testSpan.setAttribute("double", 3.0)
    testSpan.setAttribute("boolean", true)
    testSpan.setStatus(StatusCanonicalCode.ERROR)
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "test"
          kind io.opentelemetry.trace.Span.Kind.PRODUCER
          hasNoParent()
          status io.opentelemetry.trace.StatusCanonicalCode.ERROR
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
    def tracer = OpenTelemetry.getTracer("test")
    Span parentSpan = tracer.spanBuilder("parent").startSpan()
    Scope parentScope = tracer.withSpan(parentSpan)

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

  def "capture span with implicit parent using TracingContextUtils.currentContextWith()"() {
    when:
    def tracer = OpenTelemetry.getTracer("test")
    Span parentSpan = tracer.spanBuilder("parent").startSpan()
    Scope parentScope = currentContextWith(parentSpan)

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

  def "capture span with implicit parent using TracingContextUtils.withSpan and ContextUtils.withScopedContext()"() {
    when:
    def tracer = OpenTelemetry.getTracer("test")
    Span parentSpan = tracer.spanBuilder("parent").startSpan()
    def parentContext = withSpan(parentSpan, Context.current())
    Scope parentScope = withScopedContext(parentContext)

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
    def tracer = OpenTelemetry.getTracer("test")
    def parentSpan = tracer.spanBuilder("parent").startSpan()
    def context = withSpan(parentSpan, Context.ROOT)
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
    def tracer = OpenTelemetry.getTracer("test")
    def parentSpan = tracer.spanBuilder("parent").startSpan()
    def parentScope = currentContextWith(parentSpan)
    def testSpan = tracer.spanBuilder("test").setNoParent().startSpan()
    testSpan.end()
    parentSpan.end()
    parentScope.close()

    then:
    assertTraces(2) {
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
    def tracer = OpenTelemetry.getTracer("test")
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
    def tracer = OpenTelemetry.getTracer("test")
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
              "${SemanticAttributes.EXCEPTION_TYPE.key()}" "java.lang.IllegalStateException"
              "${SemanticAttributes.EXCEPTION_STACKTRACE.key()}" String
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
    def tracer = OpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.recordException(
      new IllegalStateException(),
      Attributes.newBuilder().setAttribute("dog", "bark").build())
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "test"
          event(0) {
            eventName("exception")
            attributes {
              "${SemanticAttributes.EXCEPTION_TYPE.key()}" "java.lang.IllegalStateException"
              "${SemanticAttributes.EXCEPTION_STACKTRACE.key()}" String
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
    def tracer = OpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def testScope = tracer.withSpan(testSpan)
    getCurrentSpan().updateName("test2")
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

  def "capture name update using TracingContextUtils.getSpan(Context.current())"() {
    when:
    def tracer = OpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def testScope = tracer.withSpan(testSpan)
    getSpan(Context.current()).updateName("test2")
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

  def "add DefaultSpan to context"() {
    when:
    // Lazy way to get a span context
    def tracer = OpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").setSpanKind(PRODUCER).startSpan()
    testSpan.end()

    def span = DefaultSpan.create(testSpan.getContext())
    def context = withSpan(span, Context.current())

    then:
    getSpan(context).getContext().getSpanIdAsHexString() == span.getContext().getSpanIdAsHexString()
  }
}
