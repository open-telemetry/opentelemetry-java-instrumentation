import datadog.opentracing.decorators.ErrorFlag
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.ConfigUtils
import datadog.trace.api.DDTags
import datadog.trace.api.Trace
import datadog.trace.instrumentation.api.Tags
import dd.test.trace.annotation.SayTracedHello

import java.util.concurrent.Callable

class TraceAnnotationsTest extends AgentTestRunner {

  static {
    ConfigUtils.updateConfig {
      System.clearProperty("dd.trace.annotations")
    }
  }

  def "test simple case annotations"() {
    setup:
    // Test single span in new trace
    SayTracedHello.sayHello()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "trace.annotation"
          parent()
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "test"
            "$DDTags.RESOURCE_NAME" "SayTracedHello.sayHello"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
      }
    }
  }

  def "test simple case with only operation name set"() {
    setup:
    // Test single span in new trace
    SayTracedHello.sayHA()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "SAY_HA"
          spanType "DB"
          parent()
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "test"
            "$DDTags.RESOURCE_NAME" "SayTracedHello.sayHA"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
      }
    }
  }

  def "test simple case with only resource name set"() {
    setup:
    // Test single span in new trace
    SayTracedHello.sayHelloOnlyResourceSet()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "trace.annotation"
          parent()
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "test"
            "$DDTags.RESOURCE_NAME" "WORLD"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
      }
    }
  }

  def "test simple case with both resource and operation name set"() {
    setup:
    // Test single span in new trace
    SayTracedHello.sayHAWithResource()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "SAY_HA"
          spanType "DB"
          parent()
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "test"
            "$DDTags.RESOURCE_NAME" "EARTH"
            "$Tags.COMPONENT" "trace"
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
          operationName "NEW_TRACE"
          parent()
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "test2"
            "$DDTags.RESOURCE_NAME" "SayTracedHello.sayHELLOsayHA"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
        span(1) {
          operationName "SAY_HA"
          spanType "DB"
          childOf span(0)
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "test"
            "$DDTags.RESOURCE_NAME" "SayTracedHello.sayHA"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
        span(2) {
          operationName "trace.annotation"
          childOf span(0)
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "test"
            "$DDTags.RESOURCE_NAME" "SayTracedHello.sayHello"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
      }
    }
  }

  def "test complex case with resource name at top level"() {
    when:
    // Test new trace with 2 children spans
    SayTracedHello.sayHELLOsayHAWithResource()

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "NEW_TRACE"
          parent()
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "test2"
            "$DDTags.RESOURCE_NAME" "WORLD"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
        span(1) {
          operationName "SAY_HA"
          spanType "DB"
          childOf span(0)
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "test"
            "$DDTags.RESOURCE_NAME" "SayTracedHello.sayHA"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
        span(2) {
          operationName "trace.annotation"
          childOf span(0)
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "test"
            "$DDTags.RESOURCE_NAME" "SayTracedHello.sayHello"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
      }
    }
  }

  def "test complex case with resource name at various levels"() {
    when:
    // Test new trace with 2 children spans
    SayTracedHello.sayHELLOsayHAMixedResourceChildren()

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "NEW_TRACE"
          parent()
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "test2"
            "$DDTags.RESOURCE_NAME" "WORLD"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
        span(1) {
          operationName "SAY_HA"
          spanType "DB"
          childOf span(0)
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "test"
            "$DDTags.RESOURCE_NAME" "EARTH"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
        span(2) {
          operationName "trace.annotation"
          childOf span(0)
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "test"
            "$DDTags.RESOURCE_NAME" "SayTracedHello.sayHello"
            "$Tags.COMPONENT" "trace"
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
          operationName "ERROR"
          errored true
          tags {
            "$DDTags.RESOURCE_NAME" "SayTracedHello.sayERROR"
            "$Tags.COMPONENT" "trace"
            errorTags(error.class)
            defaultTags()
          }
        }
      }
    }
  }

  def "test exception exit with resource name"() {
    setup:

    TEST_TRACER.addDecorator(new ErrorFlag())

    Throwable error = null
    try {
      SayTracedHello.sayERRORWithResource()
    } catch (final Throwable ex) {
      error = ex
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "ERROR"
          errored true
          tags {
            "$DDTags.RESOURCE_NAME" "WORLD"
            "$Tags.COMPONENT" "trace"
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
          operationName "trace.annotation"
          tags {
            "$DDTags.RESOURCE_NAME" "SayTracedHello\$1.call"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
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
          operationName "trace.annotation"
          tags {
            "$DDTags.RESOURCE_NAME" "SayTracedHello\$1.call"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
        trace(1, 1) {
          span(0) {
            operationName "trace.annotation"
            tags {
              "$DDTags.RESOURCE_NAME" "TraceAnnotationsTest\$1.call"
              "$Tags.COMPONENT" "trace"
              defaultTags()
            }
          }
        }
      }
    }
  }
}
