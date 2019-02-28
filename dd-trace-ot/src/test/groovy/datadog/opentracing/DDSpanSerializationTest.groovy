package datadog.opentracing


import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Maps
import datadog.trace.api.DDTags
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import org.msgpack.core.MessagePack
import org.msgpack.core.buffer.ArrayBufferInput
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.msgpack.value.ValueType
import spock.lang.Specification

class DDSpanSerializationTest extends Specification {

  def "serialize spans with sampling #samplingPriority"() throws Exception {
    setup:
    final Map<String, String> baggage = new HashMap<>()
    baggage.put("a-baggage", "value")
    final Map<String, Object> tags = new HashMap<>()
    baggage.put("k1", "v1")

    Map<String, Object> expected = Maps.newHashMap()
    expected.put("meta", baggage)
    expected.put("service", "service")
    expected.put("error", 0)
    expected.put("type", "type")
    expected.put("name", "operation")
    expected.put("duration", 33000)
    expected.put("resource", "operation")
    final Map<String, Number> metrics = new HashMap<>()
    if (samplingPriority != PrioritySampling.UNSET) {
      metrics.put("_sampling_priority_v1", Integer.valueOf(samplingPriority))
      metrics.put("_sample_rate", Double.valueOf(1.0))
    }
    expected.put("metrics", metrics)
    expected.put("start", 100000)
    expected.put("span_id", 2l)
    expected.put("parent_id", 0l)
    expected.put("trace_id", 1l)

    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext context =
      new DDSpanContext(
        "1",
        "2",
        "0",
        "service",
        "operation",
        null,
        samplingPriority,
        null,
        new HashMap<>(baggage),
        false,
        "type",
        tags,
        new PendingTrace(tracer, "1", [:]),
        tracer)

    baggage.put(DDTags.THREAD_NAME, Thread.currentThread().getName())
    baggage.put(DDTags.THREAD_ID, String.valueOf(Thread.currentThread().getId()))

    DDSpan span = new DDSpan(100L, context)
    if (samplingPriority != PrioritySampling.UNSET) {
      span.context().setMetric("_sample_rate", Double.valueOf(1.0))
    }
    span.finish(133L)
    ObjectMapper serializer = new ObjectMapper()

    def actualTree = serializer.readTree(serializer.writeValueAsString(span))
    def expectedTree = serializer.readTree(serializer.writeValueAsString(expected))
    expect:
    actualTree == expectedTree

    where:
    samplingPriority              | _
    PrioritySampling.SAMPLER_KEEP | _
    PrioritySampling.UNSET        | _
  }

  def "serialize trace/span with id #value as int"() {
    setup:
    def objectMapper = new ObjectMapper(new MessagePackFactory())
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    def context = new DDSpanContext(
      value.toString(),
      value.toString(),
      "0",
      "fakeService",
      "fakeOperation",
      "fakeResource",
      PrioritySampling.UNSET,
      null,
      Collections.emptyMap(),
      false,
      "fakeType",
      Collections.emptyMap(),
      new PendingTrace(tracer, "1", [:]),
      tracer)
    def span = new DDSpan(0, context)
    byte[] bytes = objectMapper.writeValueAsBytes(span)
    def unpacker = MessagePack.newDefaultUnpacker(new ArrayBufferInput(bytes))
    int size = unpacker.unpackMapHeader()

    expect:
    for (int i = 0; i < size; i++) {
      String key = unpacker.unpackString()

      switch (key) {
        case "trace_id":
        case "span_id":
          assert unpacker.nextFormat.valueType == ValueType.INTEGER
          assert unpacker.unpackBigInteger() == value
          break
        default:
          unpacker.unpackValue()
      }
    }

    where:
    value                                                       | _
    BigInteger.ZERO                                             | _
    BigInteger.ONE                                              | _
    BigInteger.valueOf(Long.MAX_VALUE).subtract(BigInteger.ONE) | _
    BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)      | _
  }
}
