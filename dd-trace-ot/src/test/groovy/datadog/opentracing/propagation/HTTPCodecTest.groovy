package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.TraceCollection
import datadog.trace.common.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import io.opentracing.propagation.TextMapExtractAdapter
import io.opentracing.propagation.TextMapInjectAdapter
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

@Timeout(1)
class HTTPCodecTest extends Specification {
  @Shared
  private static final String OT_BAGGAGE_PREFIX = "ot-baggage-"
  @Shared
  private static final String TRACE_ID_KEY = "x-datadog-trace-id"
  @Shared
  private static final String SPAN_ID_KEY = "x-datadog-parent-id"
  @Shared
  private static final String SAMPLING_PRIORITY_KEY = "x-datadog-sampling-priority"

  @Unroll
  def "inject http headers"() {
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
        new DDSpanContext(
          1L,
          2L,
          0L,
          "fakeService",
          "fakeOperation",
          "fakeResource",
          samplingPriority,
          new HashMap<String, String>() {
            {
              put("k1", "v1")
              put("k2", "v2")
            }
          },
          false,
          "fakeType",
          null,
          new TraceCollection(tracer),
          tracer)

    final Map<String, String> carrier = new HashMap<>()

    final HTTPCodec codec = new HTTPCodec()
    codec.inject(mockedContext, new TextMapInjectAdapter(carrier))

    expect:
    carrier.get(TRACE_ID_KEY) == "1"
    carrier.get(SPAN_ID_KEY) == "2"
    carrier.get(SAMPLING_PRIORITY_KEY) == (samplingPriority == PrioritySampling.UNSET ? null : String.valueOf(samplingPriority))
    carrier.get(OT_BAGGAGE_PREFIX + "k1") == "v1"
    carrier.get(OT_BAGGAGE_PREFIX + "k2") == "v2"

    where:
    samplingPriority                    | _
    PrioritySampling.UNSET         | _
    PrioritySampling.SAMPLER_KEEP  | _
  }

  @Unroll
  def "extract http headers"() {
    setup:
    final Map<String, String> actual =
        new HashMap<String, String>() {
          {
            put(TRACE_ID_KEY.toUpperCase(), "1")
            put(SPAN_ID_KEY.toUpperCase(), "2")
            put(OT_BAGGAGE_PREFIX.toUpperCase() + "k1", "v1")
            put(OT_BAGGAGE_PREFIX.toUpperCase() + "k2", "v2")
          }
        }

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, String.valueOf(samplingPriority))
    }

    final HTTPCodec codec = new HTTPCodec()
    final ExtractedContext context = codec.extract(new TextMapExtractAdapter(actual))

    expect:
    context.getTraceId() == 1l
    context.getSpanId() == 2l
    context.getBaggage().get("k1") == "v1"
    context.getBaggage().get("k2") == "v2"
    context.getSamplingPriority() == samplingPriority

    where:
    samplingPriority                    | _
    PrioritySampling.UNSET         | _
    PrioritySampling.SAMPLER_KEEP  | _
  }
}
