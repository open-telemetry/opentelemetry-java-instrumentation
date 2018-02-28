import datadog.opentracing.decorators.ErrorFlag
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.Trace
import dd.test.trace.annotation.SayTracedHello

import java.util.concurrent.Callable

import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class TraceAnnotationsTest extends AgentTestRunner {

  def "test simple case annotations"() {
    setup:
    // Test single span in new trace
    SayTracedHello.sayHello()

    expect:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "test"
          resourceName "SayTracedHello.sayHello"
          operationName "SayTracedHello.sayHello"
          parent()
          errored false
          tags {
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
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        span(0) {
          resourceName "NEW_TRACE"
          operationName "NEW_TRACE"
          parent()
          errored false
          tags {
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
            "span.type" "DB"
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
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          resourceName "ERROR"
          operationName "ERROR"
          errored true
          tags {
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
    assertTraces(TEST_WRITER, 1) {
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
    assertTraces(TEST_WRITER, 2) {
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

  def "test configuration based trace"() {
    expect:
    new ConfigTracedCallable().call() == "Hello!"

    when:
    TEST_WRITER.waitForTraces(1)

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          resourceName "ConfigTracedCallable.call"
          operationName "ConfigTracedCallable.call"
        }
      }
    }
  }

  static {
    System.setProperty("dd.trace.methods", "package.ClassName[method1,method2];${ConfigTracedCallable.name}[call]")
  }

  class ConfigTracedCallable implements Callable<String> {
    @Override
    String call() throws Exception {
      return "Hello!"
    }
  }
}
