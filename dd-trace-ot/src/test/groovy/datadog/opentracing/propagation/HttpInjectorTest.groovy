package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.Config
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.propagation.TextMapInjectAdapter

import static datadog.trace.api.Config.PropagationStyle.DATADOG

class HttpInjectorTest extends DDSpecification {

  def "inject http headers"() {
    setup:
    Config config = Mock(Config) {
      getPropagationStylesToInject() >> [DATADOG]
    }
    HttpCodec.Injector injector = HttpCodec.createInjector(config)

    def traceId = 1G
    def spanId = 2G

    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        traceId,
        spanId,
        0G,
        "fakeService",
        "fakeOperation",
        "fakeResource",
        new HashMap<String, String>() {
          {
            put("k1", "v1")
            put("k2", "v2")
          }
        },
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, 1G),
        tracer)

    final Map<String, String> carrier = Mock()

    when:
    injector.inject(mockedContext, new TextMapInjectAdapter(carrier))

    then:
    1 * carrier.put(DatadogHttpCodec.TRACE_ID_KEY, traceId.toString())
    1 * carrier.put(DatadogHttpCodec.SPAN_ID_KEY, spanId.toString())
    1 * carrier.put(DatadogHttpCodec.OT_BAGGAGE_PREFIX + "k1", "v1")
    1 * carrier.put(DatadogHttpCodec.OT_BAGGAGE_PREFIX + "k2", "v2")
    0 * _
  }
}
