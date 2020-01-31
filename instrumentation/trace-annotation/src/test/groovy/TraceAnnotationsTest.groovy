import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils
import io.opentelemetry.test.annotation.SayTracedHello
import io.opentracing.contrib.dropwizard.Trace

import java.util.concurrent.Callable

class TraceAnnotationsTest extends AgentTestRunner {

  static {
    ConfigUtils.updateConfig {
      System.clearProperty("opentelemetry.auto.trace.annotations")
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
            "$MoreTags.SERVICE_NAME" "test"
            "$MoreTags.RESOURCE_NAME" "SayTracedHello.sayHello"
            "$Tags.COMPONENT" "trace"
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
          operationName "trace.annotation"
          parent()
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" "test2"
            "$MoreTags.RESOURCE_NAME" "SayTracedHello.sayHELLOsayHA"
            "$Tags.COMPONENT" "trace"
          }
        }
        span(1) {
          operationName "trace.annotation"
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" "test"
            "$MoreTags.RESOURCE_NAME" "SayTracedHello.sayHello"
            "$Tags.COMPONENT" "trace"
          }
        }
        span(2) {
          operationName "trace.annotation"
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" "test"
            "$MoreTags.RESOURCE_NAME" "SayTracedHello.sayHello"
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }
  }

  def "test exception exit"() {
    setup:

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
          operationName "trace.annotation"
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "SayTracedHello.sayERROR"
            "$Tags.COMPONENT" "trace"
            errorTags(error.class)
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
            "$MoreTags.RESOURCE_NAME" "SayTracedHello\$1.call"
            "$Tags.COMPONENT" "trace"
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

    then:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          operationName "trace.annotation"
          tags {
            "$MoreTags.RESOURCE_NAME" "SayTracedHello\$1.call"
            "$Tags.COMPONENT" "trace"
          }
        }
        trace(1, 1) {
          span(0) {
            operationName "trace.annotation"
            tags {
              "$MoreTags.RESOURCE_NAME" "TraceAnnotationsTest\$1.call"
              "$Tags.COMPONENT" "trace"
            }
          }
        }
      }
    }
  }
}
