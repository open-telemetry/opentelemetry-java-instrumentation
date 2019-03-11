import datadog.opentracing.decorators.ErrorFlag
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.Trace
import dd.test.trace.annotation.SayTracedHello
import io.opentracing.tag.Tags

import java.util.concurrent.Callable

class TraceAnnotationsTest extends AgentTestRunner {

  static {
    System.clearProperty("dd.trace.annotations")
  }

  def "test simple case annotations"() {
    setup:
    // Test single span in new trace
    SayTracedHello.sayHello()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "test"
          resourceName "SayTracedHello.sayHello"
          operationName "SayTracedHello.sayHello"
          parent()
          errored false
          tags {
            "$Tags.COMPONENT.key" "trace"
            defaultTags()
          }
        }
      }
    }
  }

  def "test complex case annotations"() {
    when:
    // Test new trace with 2 children spans
    SayTracedHello.sayHELLOsayHA()

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          resourceName "NEW_TRACE"
          operationName "NEW_TRACE"
          parent()
          errored false
          tags {
            "$Tags.COMPONENT.key" "trace"
            defaultTags()
          }
        }
        span(1) {
          resourceName "SAY_HA"
          operationName "SAY_HA"
          spanType "DB"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "trace"
            defaultTags()
          }
        }
        span(2) {
          serviceName "test"
          resourceName "SayTracedHello.sayHello"
          operationName "SayTracedHello.sayHello"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "trace"
            defaultTags()
          }
        }
      }
    }
  }

  def "test exception exit"() {
    setup:

    TEST_TRACER.addDecorator(new ErrorFlag())

    Throwable error = null
    try {
      SayTracedHello.sayERROR()
    } catch (final Throwable ex) {
      error = ex
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          resourceName "ERROR"
          operationName "ERROR"
          errored true
          tags {
            "$Tags.COMPONENT.key" "trace"
            errorTags(error.class)
            defaultTags()
          }
        }
      }
    }
  }

  def "test annonymous class annotations"() {
    setup:
    // Test anonymous classes with package.
    SayTracedHello.fromCallable()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          resourceName "SayTracedHello\$1.call"
          operationName "SayTracedHello\$1.call"
        }
      }
    }

    when:
    // Test anonymous classes with no package.
    new Callable<String>() {
      @Trace
      @Override
      String call() throws Exception {
        return "Howdy!"
      }
    }.call()
    TEST_WRITER.waitForTraces(2)

    then:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          resourceName "SayTracedHello\$1.call"
          operationName "SayTracedHello\$1.call"
        }
        trace(1, 1) {
          span(0) {
            resourceName "TraceAnnotationsTest\$1.call"
            operationName "TraceAnnotationsTest\$1.call"
          }
        }
      }
    }
  }
}
