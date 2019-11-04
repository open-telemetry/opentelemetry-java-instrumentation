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
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        traceId,
        spanId,
        BigInteger.ZERO,
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
        new PendingTrace(tracer, BigInteger.ONE, [:]),
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
    traceId               | spanId                | samplingPriority              | expectedSamplingPriority
    BigInteger.valueOf(1) | BigInteger.valueOf(2) | PrioritySampling.UNSET        | null
    BigInteger.valueOf(2) | BigInteger.valueOf(3) | PrioritySampling.SAMPLER_KEEP | 1
    BigInteger.valueOf(4) | BigInteger.valueOf(5) | PrioritySampling.SAMPLER_DROP | 0
    BigInteger.valueOf(5) | BigInteger.valueOf(6) | PrioritySampling.USER_KEEP    | 1
    BigInteger.valueOf(6) | BigInteger.valueOf(7) | PrioritySampling.USER_DROP    | 0
    TRACE_ID_MAX          | TRACE_ID_MAX - 1      | PrioritySampling.UNSET        | null
    TRACE_ID_MAX - 1      | TRACE_ID_MAX          | PrioritySampling.SAMPLER_KEEP | 1
  }
}
