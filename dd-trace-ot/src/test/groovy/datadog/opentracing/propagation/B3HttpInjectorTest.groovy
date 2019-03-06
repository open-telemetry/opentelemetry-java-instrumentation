package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import io.opentracing.propagation.TextMapInjectAdapter
import spock.lang.Specification

import static datadog.opentracing.propagation.B3HttpCodec.SAMPLING_PRIORITY_KEY
import static datadog.opentracing.propagation.B3HttpCodec.SPAN_ID_KEY
import static datadog.opentracing.propagation.B3HttpCodec.TRACE_ID_KEY
import static datadog.opentracing.propagation.HttpCodec.UINT64_MAX

class B3HttpInjectorTest extends Specification {

  HttpCodec.Injector injector = new B3HttpCodec.Injector()

  def "inject http headers"() {
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        traceId,
        spanId,
        "0",
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
        new PendingTrace(tracer, "1", [:]),
        tracer)

    final Map<String, String> carrier = Mock()

    when:
    injector.inject(mockedContext, new TextMapInjectAdapter(carrier))

    then:
    1 * carrier.put(TRACE_ID_KEY, new BigInteger(traceId).toString(16).toLowerCase())
    1 * carrier.put(SPAN_ID_KEY, new BigInteger(spanId).toString(16).toLowerCase())
    if (expectedSamplingPriority != null) {
      1 * carrier.put(SAMPLING_PRIORITY_KEY, "$expectedSamplingPriority")
    }
    0 * _

    where:
    traceId                        | spanId                         | samplingPriority              | expectedSamplingPriority
    "1"                            | "2"                            | PrioritySampling.UNSET        | null
    "2"                            | "3"                            | PrioritySampling.SAMPLER_KEEP | 1
    "4"                            | "5"                            | PrioritySampling.SAMPLER_DROP | 0
    "5"                            | "6"                            | PrioritySampling.USER_KEEP    | 1
    "6"                            | "7"                            | PrioritySampling.USER_DROP    | 0
    UINT64_MAX.toString()          | UINT64_MAX.minus(1).toString() | PrioritySampling.UNSET        | null
    UINT64_MAX.minus(1).toString() | UINT64_MAX.toString()          | PrioritySampling.SAMPLER_KEEP | 1
  }

  def "unparseable ids"() {
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        traceId,
        spanId,
        "0",
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
        new PendingTrace(tracer, "1", [:]),
        tracer)

    final Map<String, String> carrier = Mock()

    when:
    injector.inject(mockedContext, new TextMapInjectAdapter(carrier))

    then:
    0 * _

    where:
    traceId | spanId | samplingPriority
    "abc"   | "1"    | PrioritySampling.UNSET
    "1"     | "cbd"  | PrioritySampling.SAMPLER_KEEP
  }
}
