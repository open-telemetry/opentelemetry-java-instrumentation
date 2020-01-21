import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.instrumentation.trace_annotation.TraceAnnotationsInstrumentation
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils
import io.opentelemetry.test.annotation.SayTracedHello

import java.util.concurrent.Callable

import static io.opentelemetry.auto.instrumentation.trace_annotation.TraceAnnotationsInstrumentation.DEFAULT_ANNOTATIONS

class ConfiguredTraceAnnotationsTest extends AgentTestRunner {

  static {
    ConfigUtils.updateConfig {
      System.setProperty("opentelemetry.auto.trace.annotations", "package.Class\$Name;${OuterClass.InterestingMethod.name}")
    }
  }

  def specCleanup() {
    System.clearProperty("opentelemetry.auto.trace.annotations")
  }

  def "test disabled nr annotation"() {
    setup:
    SayTracedHello.fromCallableWhenDisabled()

    expect:
    TEST_WRITER.traces == []
  }

  def "test custom annotation based trace"() {
    expect:
    new AnnotationTracedCallable().call() == "Hello!"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "trace.annotation"
          tags {
            "$MoreTags.RESOURCE_NAME" "AnnotationTracedCallable.call"
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
        System.properties.setProperty("opentelemetry.auto.trace.annotations", value)
      } else {
        System.clearProperty("opentelemetry.auto.trace.annotations")
      }
    }

    expect:
    new TraceAnnotationsInstrumentation().additionalTraceAnnotations == expected.toSet()

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
