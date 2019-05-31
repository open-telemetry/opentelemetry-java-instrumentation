import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.instrumentation.trace_annotation.TraceConfigInstrumentation

import java.util.concurrent.Callable

import static datadog.trace.agent.test.utils.ConfigUtils.withSystemProperty

class TraceConfigTest extends AgentTestRunner {

  static {
    System.setProperty("dd.trace.methods", "package.ClassName[method1,method2];${ConfigTracedCallable.name}[call]")
  }

  def specCleanup() {
    System.clearProperty("dd.trace.methods")
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

    when:
    TEST_WRITER.waitForTraces(1)

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          resourceName "ConfigTracedCallable.call"
          operationName "ConfigTracedCallable.call"
        }
      }
    }
  }

  def "test configuration #value"() {
    setup:
    def config = null
    withSystemProperty("dd.trace.methods", value) {
      def instrumentation = new TraceConfigInstrumentation()
      config = instrumentation.classMethodsToTrace
    }
    expect:

    config == expected

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
