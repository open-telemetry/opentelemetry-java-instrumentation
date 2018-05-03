import datadog.opentracing.DDSpan
import datadog.opentracing.decorators.ErrorFlag
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.Trace
import dd.test.trace.annotation.SayTracedHello
import org.assertj.core.api.Assertions

import java.util.concurrent.Callable

class TraceAnnotationsTest extends AgentTestRunner {

  def "test simple case annotations"() {
    setup:
    // Test single span in new trace
    SayTracedHello.sayHello()

    Assertions.assertThat(TEST_WRITER.firstTrace().size()).isEqualTo(1)
    Assertions.assertThat(TEST_WRITER.firstTrace().get(0).getOperationName())
      .isEqualTo("SayTracedHello.sayHello")
    Assertions.assertThat(TEST_WRITER.firstTrace().get(0).getServiceName()).isEqualTo("test")
  }

  def "test complex case annotations"() {
    setup:

    // Test new trace with 2 children spans
    SayTracedHello.sayHELLOsayHA()
    Assertions.assertThat(TEST_WRITER.firstTrace().size()).isEqualTo(3)
    final long parentId = TEST_WRITER.firstTrace().get(0).context().getSpanId()

    Assertions.assertThat(TEST_WRITER.firstTrace().get(0).getOperationName()).isEqualTo("NEW_TRACE")
    Assertions.assertThat(TEST_WRITER.firstTrace().get(0).getParentId())
      .isEqualTo(0) // ROOT / no parent
    Assertions.assertThat(TEST_WRITER.firstTrace().get(0).context().getParentId()).isEqualTo(0)
    Assertions.assertThat(TEST_WRITER.firstTrace().get(0).getServiceName()).isEqualTo("test2")

    Assertions.assertThat(TEST_WRITER.firstTrace().get(1).getOperationName()).isEqualTo("SAY_HA")
    Assertions.assertThat(TEST_WRITER.firstTrace().get(1).getParentId()).isEqualTo(parentId)
    Assertions.assertThat(TEST_WRITER.firstTrace().get(1).context().getSpanType()).isEqualTo("DB")

    Assertions.assertThat(TEST_WRITER.firstTrace().get(2).getOperationName())
      .isEqualTo("SayTracedHello.sayHello")
    Assertions.assertThat(TEST_WRITER.firstTrace().get(2).getServiceName()).isEqualTo("test")
    Assertions.assertThat(TEST_WRITER.firstTrace().get(2).getParentId()).isEqualTo(parentId)
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

    final StringWriter errorString = new StringWriter()
    error.printStackTrace(new PrintWriter(errorString))

    final DDSpan span = TEST_WRITER.firstTrace().get(0)
    Assertions.assertThat(span.getOperationName()).isEqualTo("ERROR")
    Assertions.assertThat(span.getTags().get("error")).isEqualTo(true)
    Assertions.assertThat(span.getTags().get("error.msg")).isEqualTo(error.getMessage())
    Assertions.assertThat(span.getTags().get("error.type")).isEqualTo(error.getClass().getName())
    Assertions.assertThat(span.getTags().get("error.stack")).isEqualTo(errorString.toString())
    Assertions.assertThat(span.getError()).isEqualTo(1)
  }

  def "test annonymous class annotations"() {
    setup:
    // Test anonymous classes with package.
    SayTracedHello.fromCallable()

    Assertions.assertThat(TEST_WRITER.size()).isEqualTo(1)
    Assertions.assertThat(TEST_WRITER.firstTrace().size()).isEqualTo(1)
    Assertions.assertThat(TEST_WRITER.firstTrace().get(0).getOperationName())
      .isEqualTo("SayTracedHello\$1.call")

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
    Assertions.assertThat(TEST_WRITER.size()).isEqualTo(2)
    Assertions.assertThat(TEST_WRITER.get(1).size()).isEqualTo(1)
    Assertions.assertThat(TEST_WRITER.get(1).get(0).getOperationName())
      .isEqualTo("TraceAnnotationsTest\$1.call")
  }
}
