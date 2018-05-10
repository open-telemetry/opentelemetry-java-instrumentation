import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.instrumentation.trace_annotation.TraceAnnotationsInstrumentation
import dd.test.trace.annotation.SayTracedHello

import java.util.concurrent.Callable

import static TraceAnnotationsInstrumentation.DEFAULT_ANNOTATIONS
import static datadog.trace.agent.test.ListWriterAssert.assertTraces
import static datadog.trace.agent.test.TestUtils.withSystemProperty

class ConfiguredTraceAnnotationsTest extends AgentTestRunner {

  static {
    // nr annotation not included here, so should be disabled.
    System.setProperty("dd.trace.annotations", "package.Class\$Name;${OuterClass.InterestingMethod.name}")
  }

  def specCleanup() {
    System.clearProperty("dd.trace.annotations")
  }

  def "test disabled nr annotation"() {
    setup:
    SayTracedHello.fromCallableWhenDisabled()

    expect:
    TEST_WRITER == []
  }

  def "test custom annotation based trace"() {
    expect:
    new AnnotationTracedCallable().call() == "Hello!"

    when:
    TEST_WRITER.waitForTraces(1)

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          resourceName "AnnotationTracedCallable.call"
          operationName "AnnotationTracedCallable.call"
        }
      }
    }
  }

  def "test configuration #value"() {
    setup:
    def config = null
    withSystemProperty("dd.trace.annotations", value) {
      def instrumentation = new TraceAnnotationsInstrumentation()
      config = instrumentation.additionalTraceAnnotations
    }
    expect:
    config == expected.toSet()

    where:
    value                               | expected
    null                                | DEFAULT_ANNOTATIONS.toList()
    " "                                 | []
    "some.Invalid[]"                    | []
    "some.package.ClassName "           | ["some.package.ClassName"]
    " some.package.Class\$Name"         | ["some.package.Class\$Name"]
    "  ClassName  "                     | ["ClassName"]
    "ClassName"                         | ["ClassName"]
    "Class\$1;Class\$2;"                | ["Class\$1", "Class\$2"]
    "Duplicate ;Duplicate ;Duplicate; " | ["Duplicate"]
  }

  class AnnotationTracedCallable implements Callable<String> {
    @OuterClass.InterestingMethod
    @Override
    String call() throws Exception {
      return "Hello!"
    }
  }
}
