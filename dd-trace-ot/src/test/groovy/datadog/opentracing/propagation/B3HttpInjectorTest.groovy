package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.propagation.TextMapInjectAdapter

import static datadog.opentracing.DDTracer.TRACE_ID_MAX
import static datadog.opentracing.propagation.B3HttpCodec.SAMPLING_PRIORITY_KEY
import static datadog.opentracing.propagation.B3HttpCodec.SPAN_ID_KEY
import static datadog.opentracing.propagation.B3HttpCodec.TRACE_ID_KEY

class B3HttpInjectorTest extends DDSpecification {

  HttpCodec.Injector injector = new B3HttpCodec.Injector()

  def "inject http headers"() {
    setup:
    def writer = new ListWriter()
    def tracer = DDTracer.builder().writer(writer).build()
    final DDSpanContext mockedContext =
      new DDSpanContext(
        traceId,
        spanId,
        0G,
        "fakeService",
        "fakeOperation",
        "fakeResource",
        samplingPriority,
        "fakeOrigin",
        new HashMap<String, String>() {
          {
            put("k1", "v1")
            put("k2", "v2")
          }
        },
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, 1G, [:]),
        tracer)

    final Map<String, String> carrier = Mock()

    when:
    injector.inject(mockedContext, new TextMapInjectAdapter(carrier))

    then:
    1 * carrier.put(TRACE_ID_KEY, traceId.toString(16).toLowerCase())
    1 * carrier.put(SPAN_ID_KEY, spanId.toString(16).toLowerCase())
    if (expectedSamplingPriority != null) {
      1 * carrier.put(SAMPLING_PRIORITY_KEY, "$expectedSamplingPriority")
    }
    0 * _

    where:
    traceId          | spanId           | samplingPriority              | expectedSamplingPriority
    1G               | 2G               | PrioritySampling.UNSET        | null
    2G               | 3G               | PrioritySampling.SAMPLER_KEEP | 1
    4G               | 5G               | PrioritySampling.SAMPLER_DROP | 0
    5G               | 6G               | PrioritySampling.USER_KEEP    | 1
    6G               | 7G               | PrioritySampling.USER_DROP    | 0
    TRACE_ID_MAX     | TRACE_ID_MAX - 1 | PrioritySampling.UNSET        | null
    TRACE_ID_MAX - 1 | TRACE_ID_MAX     | PrioritySampling.SAMPLER_KEEP | 1
  }
}
