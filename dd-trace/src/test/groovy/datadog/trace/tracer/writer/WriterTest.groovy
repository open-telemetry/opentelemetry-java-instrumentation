package datadog.trace.tracer.writer

import datadog.trace.api.Config
import spock.lang.Specification


class WriterTest extends Specification {

  def "test builder logging writer"() {
    setup:
    def config = Mock(Config) {
      getWriterType() >> Config.LOGGING_WRITER_TYPE
    }

    when:
    def writer = Writer.Builder.forConfig(config)

    then:
    writer instanceof LoggingWriter
  }

  def "test builder logging writer properties"() {
    setup:
    def properties = new Properties()
    properties.setProperty(Config.WRITER_TYPE, Config.LOGGING_WRITER_TYPE)

    when:
    def writer = Writer.Builder.forConfig(properties)

    then:
    writer instanceof LoggingWriter
  }

  def "test builder agent writer: '#writerType'"() {
    setup:
    def config = Mock(Config) {
      getWriterType() >> writerType
      getAgentHost() >> "test.host"
      getAgentPort() >> 1234
    }

    when:
    def writer = Writer.Builder.forConfig(config)

    then:
    writer instanceof AgentWriter
    ((AgentWriter) writer).getAgentUrl() == new URL("http://test.host:1234/v0.4/traces");

    where:
    writerType | _
    Config.DD_AGENT_WRITER_TYPE | _
    "some odd string" | _
  }

  def "test builder no config"() {
    when:
    Writer.Builder.forConfig(null)

    then:
    thrown NullPointerException
  }

}
