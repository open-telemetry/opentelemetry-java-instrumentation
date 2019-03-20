package datadog.trace.api.writer


import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.msgpack.core.MessagePack
import org.msgpack.jackson.dataformat.MessagePackFactory
import spock.lang.Shared
import spock.lang.Specification

import static java.util.Collections.singletonMap

class SerializationTest extends Specification {
  @Shared
  def jsonMapper = new ObjectMapper()
  @Shared
  def mpMapper = new ObjectMapper(new MessagePackFactory())

  
  def "test json mapper serialization"() {
    setup:
    def map = ["key1": "val1"]
    def serializedMap = mapper.writeValueAsBytes(map)
    def serializedList = "[${new String(serializedMap)}]".getBytes()

    when:
    def result = mapper.readValue(serializedList, new TypeReference<List<Map<String, String>>>() {})

    then:
    result == [map]
    new String(serializedList) == '[{"key1":"val1"}]'

    where:
    mapper = jsonMapper
  }

  def "test msgpack mapper serialization"() {
    setup:
    def serializedMaps = input.collect {
      mapper.writeValueAsBytes(it)
    }

    def packer = MessagePack.newDefaultBufferPacker()
    packer.packArrayHeader(serializedMaps.size())
    serializedMaps.each {
      packer.writePayload(it)
    }
    def serializedList = packer.toByteArray()

    when:
    def result = mapper.readValue(serializedList, new TypeReference<List<Map<String, String>>>() {})

    then:
    result == input

    where:
    mapper = mpMapper

    // GStrings get odd results in the serializer.
    input = (1..1).collect { singletonMap("key$it".toString(), "val$it".toString()) }
  }
}
