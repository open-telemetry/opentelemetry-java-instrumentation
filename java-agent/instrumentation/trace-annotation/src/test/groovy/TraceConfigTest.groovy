import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.instrumentation.trace_annotation.TraceConfigInstrumentation
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils

import java.util.concurrent.Callable

class TraceConfigTest extends AgentTestRunner {

  static {
    ConfigUtils.updateConfig {
      System.setProperty("opentelemetry.auto.trace.methods", "package.ClassName[method1,method2];${ConfigTracedCallable.name}[call]")
    }
  }

  def specCleanup() {
    ConfigUtils.updateConfig {
      System.clearProperty("opentelemetry.auto.trace.methods")
    }
  }

  class ConfigTracedCallable implements Callable<String> {
    @Override
    String call() throws Exception {
      return "Hello!"
    }
  }

  def "test configuration based trace"() {
    expect:
    new ConfigTracedCallable().call() == "Hello!"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "trace.annotation"
          tags {
            "$MoreTags.RESOURCE_NAME" "ConfigTracedCallable.call"
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }
  }

  def "test configuration #value"() {
    setup:
    ConfigUtils.updateConfig {
      if (value) {
        System.properties.setProperty("opentelemetry.auto.trace.methods", value)
      } else {
        System.clearProperty("opentelemetry.auto.trace.methods")
      }
    }

    expect:
    new TraceConfigInstrumentation().classMethodsToTrace == expected

    cleanup:
    System.clearProperty("opentelemetry.auto.trace.methods")

    where:
    value                                                           | expected
    null                                                            | [:]
    " "                                                             | [:]
    "some.package.ClassName"                                        | [:]
    "some.package.ClassName[ , ]"                                   | [:]
    "some.package.ClassName[ , method]"                             | [:]
    "some.package.Class\$Name[ method , ]"                          | ["some.package.Class\$Name": ["method"].toSet()]
    "ClassName[ method1,]"                                          | ["ClassName": ["method1"].toSet()]
    "ClassName[method1 , method2]"                                  | ["ClassName": ["method1", "method2"].toSet()]
    "Class\$1[method1 ] ; Class\$2[ method2];"                      | ["Class\$1": ["method1"].toSet(), "Class\$2": ["method2"].toSet()]
    "Duplicate[method1] ; Duplicate[method2]  ;Duplicate[method3];" | ["Duplicate": ["method3"].toSet()]
  }
}
