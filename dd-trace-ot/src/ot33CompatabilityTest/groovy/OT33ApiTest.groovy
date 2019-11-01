import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.Tracer
import spock.lang.Subject

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces
import static datadog.trace.agent.test.utils.TraceUtils.basicSpan

// This test focuses on things that are different between OpenTracing API 0.32.0 and 0.33.0
class OT33ApiTest extends DDSpecification {
  static final WRITER = new ListWriter()

  @Subject
  Tracer tracer = new DDTracer(WRITER)

  def "test start"() {
    when:
    def span = tracer.buildSpan("some name").start()
    def scope = tracer.activateSpan(span)
    scope.close()

    then:
    (scope.span() as DDSpan).isFinished() == false
    assertTraces(WRITER, 0) {}

    when:
    span.finish()

    then:
    assertTraces(WRITER, 1) {
      trace(0, 1) {
        basicSpan(it, 0, "some name")
      }
    }
  }

  def "test scopemanager"() {
    setup:
    def span = tracer.buildSpan("some name").start()

    when:
    tracer.scopeManager().activate(span) != null

    then:
    tracer.activeSpan() == span
  }
}
