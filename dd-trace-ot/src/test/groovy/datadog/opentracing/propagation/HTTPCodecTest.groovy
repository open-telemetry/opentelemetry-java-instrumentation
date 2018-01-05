package datadog.opentracing.propagation

import com.datadoghq.trace.DDSpanContext
import io.opentracing.propagation.TextMapExtractAdapter
import io.opentracing.propagation.TextMapInjectAdapter
import spock.lang.Shared
import spock.lang.Specification

class HTTPCodecTest extends Specification {
  @Shared
  private static final String OT_BAGGAGE_PREFIX = "ot-baggage-"
  @Shared
  private static final String TRACE_ID_KEY = "x-datadog-trace-id"
  @Shared
  private static final String SPAN_ID_KEY = "x-datadog-parent-id"

  def "inject http headers"() {
    setup:
    final DDSpanContext mockedContext =
        new DDSpanContext(
            1L,
            2L,
            0L,
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
            null,
            null)

    final Map<String, String> carrier = new HashMap<>()

    final HTTPCodec codec = new HTTPCodec()
    codec.inject(mockedContext, new TextMapInjectAdapter(carrier))

    expect:
    carrier.get(TRACE_ID_KEY) == "1"
    carrier.get(SPAN_ID_KEY) == "2"
    carrier.get(OT_BAGGAGE_PREFIX + "k1") == "v1"
    carrier.get(OT_BAGGAGE_PREFIX + "k2") == "v2"
  }

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

    final HTTPCodec codec = new HTTPCodec()
    final DDSpanContext context = codec.extract(new TextMapExtractAdapter(actual))

    expect:
    context.getTraceId() == 1l
    context.getSpanId() == 2l
    context.getBaggageItem("k1") == "v1"
    context.getBaggageItem("k2") == "v2"
  }
}
