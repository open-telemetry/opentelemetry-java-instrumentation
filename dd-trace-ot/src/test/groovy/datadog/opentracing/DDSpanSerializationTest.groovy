package datadog.opentracing

import com.fasterxml.jackson.databind.ObjectMapper
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import org.msgpack.core.MessagePack
import org.msgpack.core.buffer.ArrayBufferInput
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.msgpack.value.ValueType

class DDSpanSerializationTest extends DDSpecification {

  def "serialize trace/span with id #value as int"() {
    setup:
    def objectMapper = new ObjectMapper(new MessagePackFactory())
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    def context = new DDSpanContext(
      value,
      value,
      0G,
      "fakeService",
      "fakeOperation",
      "fakeResource",
      Collections.emptyMap(),
      false,
      "fakeType",
      Collections.emptyMap(),
      new PendingTrace(tracer, 1G),
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
    value                                           | _
    0G                                              | _
    1G                                              | _
    8223372036854775807G                            | _
    BigInteger.valueOf(Long.MAX_VALUE).subtract(1G) | _
    BigInteger.valueOf(Long.MAX_VALUE).add(1G)      | _
    2G.pow(64).subtract(1G)                         | _
  }
}
