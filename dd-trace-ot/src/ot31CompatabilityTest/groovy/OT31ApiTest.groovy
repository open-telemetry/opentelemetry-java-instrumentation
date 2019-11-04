import datadog.opentracing.DDSpan
import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.propagation.ExtractedContext
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.Tracer
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMap
import spock.lang.Subject

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces
import static datadog.trace.agent.test.utils.TraceUtils.basicSpan

// This test focuses on things that are different between OpenTracing API 0.31.0 and 0.32.0
class OT31ApiTest extends DDSpecification {
  static final WRITER = new ListWriter()

  @Subject
  Tracer tracer = new DDTracer(WRITER)

  def "test startActive"() {
    when:
    def scope = tracer.buildSpan("some name").startActive(finishSpan)
    scope.close()

    then:
    (scope.span() as DDSpan).isFinished() == finishSpan

    where:
    finishSpan << [true, false]
  }

  def "test startManual"() {
    when:
    tracer.buildSpan("some name").startManual().finish()

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
    tracer.scopeManager().activate(span, finishSpan) != null
    tracer.scopeManager().active().span() == span

    then:
    tracer.scopeManager().active().close()
    (span as DDSpan).isFinished() == finishSpan

    where:
    finishSpan << [true, false]
  }

  def "test inject extract"() {
    setup:
    def context = tracer.buildSpan("some name").start().context() as DDSpanContext
    def textMap = [:]
    def adapter = new TextMapAdapter(textMap)

    when:
    tracer.inject(context, Format.Builtin.TEXT_MAP, adapter)

    then:
    textMap == [
      "x-datadog-trace-id"         : context.toTraceId(),
      "x-datadog-parent-id"        : context.toSpanId(),
      "x-datadog-sampling-priority": "$context.samplingPriority",
    ]

    when:
    def extract = tracer.extract(Format.Builtin.TEXT_MAP, adapter) as ExtractedContext

    then:
    extract.traceId == context.traceId
    extract.spanId == context.spanId
    extract.samplingPriority == context.samplingPriority
  }

  static class TextMapAdapter implements TextMap {
    private final Map<String, String> map

    TextMapAdapter(Map<String, String> map) {
      this.map = map
    }

    @Override
    Iterator<Map.Entry<String, String>> iterator() {
      return map.entrySet().iterator()
    }

    @Override
    void put(String key, String value) {
      map.put(key, value)
    }
  }
}
