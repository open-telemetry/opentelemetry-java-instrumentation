package datadog.opentracing

import com.squareup.moshi.Moshi
import datadog.trace.api.DDTags
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import org.msgpack.core.MessagePack
import org.msgpack.core.buffer.ArrayBufferInput
import org.msgpack.core.buffer.ArrayBufferOutput
import org.msgpack.value.ValueType

import static datadog.trace.common.serialization.JsonFormatWriter.SPAN_ADAPTER
import static datadog.trace.common.serialization.MsgpackFormatWriter.MSGPACK_WRITER

class DDSpanSerializationTest extends DDSpecification {

  def "serialize spans with sampling #samplingPriority"() throws Exception {
    setup:
    def jsonAdapter = new Moshi.Builder().build().adapter(Map)

    final Map<String, Number> metrics = ["_sampling_priority_v1": 1]
    metrics.putAll(DDSpanContext.DEFAULT_METRICS)
    if (samplingPriority == PrioritySampling.UNSET) {  // RateByServiceSampler sets priority
      metrics.put("_dd.agent_psr", 1.0d)
    }

    Map<String, Object> expected = [
      service  : "service",
      name     : "operation",
      resource : "operation",
      trace_id : 1l,
      span_id  : 2l,
      parent_id: 0l,
      start    : 100000,
      duration : 33000,
      type     : spanType,
      error    : 0,
      metrics  : metrics,
      meta     : [
        "a-baggage"         : "value",
        "k1"                : "v1",
        (DDTags.THREAD_NAME): Thread.currentThread().getName(),
        (DDTags.THREAD_ID)  : String.valueOf(Thread.currentThread().getId()),
      ],
    ]

    def writer = new ListWriter()
    def tracer = DDTracer.builder().writer(writer).build()
    final DDSpanContext context =
      new DDSpanContext(
        1G,
        2G,
        0G,
        "service",
        "operation",
        null,
        samplingPriority,
        null,
        ["a-baggage": "value"],
        false,
        spanType,
        ["k1": "v1"],
        new PendingTrace(tracer, 1G),
        tracer,
        [:])

    DDSpan span = new DDSpan(100L, context)

    span.finish(133L)

    def actualTree = jsonAdapter.fromJson(SPAN_ADAPTER.toJson(span))
    def expectedTree = jsonAdapter.fromJson(jsonAdapter.toJson(expected))
    expect:
    actualTree == expectedTree

    where:
    samplingPriority              | spanType
    PrioritySampling.SAMPLER_KEEP | null
    PrioritySampling.UNSET        | "some-type"
  }

  def "serialize trace/span with id #value as int"() {
    setup:
    def writer = new ListWriter()
    def tracer = DDTracer.builder().writer(writer).build()
    def context = new DDSpanContext(
      value,
      value,
      0G,
      "fakeService",
      "fakeOperation",
      "fakeResource",
      PrioritySampling.UNSET,
      null,
      Collections.emptyMap(),
      false,
      spanType,
      Collections.emptyMap(),
      new PendingTrace(tracer, 1G),
      tracer,
      [:])
    def span = new DDSpan(0, context)
    def buffer = new ArrayBufferOutput()
    def packer = MessagePack.newDefaultPacker(buffer)
    MSGPACK_WRITER.writeDDSpan(span, packer)
    packer.flush()
    byte[] bytes = buffer.toByteArray()
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
    value                                           | spanType
    0G                                              | null
    1G                                              | "some-type"
    8223372036854775807G                            | null
    BigInteger.valueOf(Long.MAX_VALUE).subtract(1G) | "some-type"
    BigInteger.valueOf(Long.MAX_VALUE).add(1G)      | null
    2G.pow(64).subtract(1G)                         | "some-type"
  }
}
