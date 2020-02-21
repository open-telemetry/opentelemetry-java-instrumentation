import io.opentelemetry.auto.test.AgentTestRunner
import unshaded.io.opentelemetry.OpenTelemetry
import unshaded.io.opentelemetry.trace.Status

import static unshaded.io.opentelemetry.trace.Span.Kind.PRODUCER

class OpenTelemetryApiTest extends AgentTestRunner {

  def "capture span, kind, attributes, and status"() {
    when:
    def tracer = OpenTelemetry.getTracerFactory().get("test")
    def testSpan = tracer.spanBuilder("test").setSpanKind(PRODUCER).startSpan()
    testSpan.setAttribute("string", "1")
    testSpan.setAttribute("long", 2)
    testSpan.setAttribute("double", 3.0)
    testSpan.setAttribute("boolean", true)
    testSpan.setStatus(Status.UNKNOWN)
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "test"
          spanKind io.opentelemetry.trace.Span.Kind.PRODUCER
          parent()
          status io.opentelemetry.trace.Status.UNKNOWN
          tags {
            "string" "1"
            "long" 2
            "double" 3.0
            "boolean" true
          }
        }
      }
    }
  }

  def "capture span with implicit parent"() {
    when:
    def tracer = OpenTelemetry.getTracerFactory().get("test")
    def parentSpan = tracer.spanBuilder("parent").startSpan()
    def parentScope = tracer.withSpan(parentSpan)
    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.end()
    parentSpan.end()
    parentScope.close()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          parent()
          tags {
          }
        }
        span(1) {
          operationName "test"
          childOf span(0)
          tags {
          }
        }
      }
    }
  }

  def "capture span with explicit parent"() {
    when:
    def tracer = OpenTelemetry.getTracerFactory().get("test")
    def parentSpan = tracer.spanBuilder("parent").startSpan()
    def testSpan = tracer.spanBuilder("test").setParent(parentSpan).startSpan()
    testSpan.end()
    parentSpan.end()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          parent()
          tags {
          }
        }
        span(1) {
          operationName "test"
          childOf span(0)
          tags {
          }
        }
      }
    }
  }

  def "capture span with explicit no parent"() {
    when:
    def tracer = OpenTelemetry.getTracerFactory().get("test")
    def parentSpan = tracer.spanBuilder("parent").startSpan()
    def parentScope = tracer.withSpan(parentSpan)
    def testSpan = tracer.spanBuilder("test").setNoParent().startSpan()
    testSpan.end()
    parentSpan.end()
    parentScope.close()

    then:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          operationName "parent"
          parent()
          tags {
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "test"
          parent()
          tags {
          }
        }
      }
    }
  }

  def "capture span with remote parent"() {
    when:
    def tracer = OpenTelemetry.getTracerFactory().get("test")
    def parentSpan = tracer.spanBuilder("parent").startSpan()
    def testSpan = tracer.spanBuilder("test").setParent(parentSpan.getContext()).startSpan()
    testSpan.end()
    parentSpan.end()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          parent()
          tags {
          }
        }
        span(1) {
          operationName "test"
          childOf span(0)
          tags {
          }
        }
      }
    }
  }

  def "capture name update"() {
    when:
    def tracer = OpenTelemetry.getTracerFactory().get("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.updateName("test2")
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "test2"
          parent()
          tags {
          }
        }
      }
    }
  }
}
