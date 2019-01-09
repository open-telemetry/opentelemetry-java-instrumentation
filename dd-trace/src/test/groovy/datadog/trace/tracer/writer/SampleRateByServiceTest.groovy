package datadog.trace.tracer.writer

import com.fasterxml.jackson.databind.ObjectMapper
import nl.jqno.equalsverifier.EqualsVerifier
import nl.jqno.equalsverifier.Warning
import spock.lang.Specification

class SampleRateByServiceTest extends Specification {

  private static final Map<String, Double> TEST_MAP = ["test": 0.1d, "another test": 0.2d]

  ObjectMapper objectMapper = new ObjectMapper()

  def "test constructor and getter"() {
    when:
    def sampleRate = new SampleRateByService(TEST_MAP)

    then:
    sampleRate.getRate("test") == 0.1d
    sampleRate.getRate("another test") == 0.2d
    sampleRate.getRate("doesn't exist") == null
  }

  def "test JSON parsing"() {
    when:
    def sampleRate = objectMapper.readValue("{\"test\": 0.8, \"another test\": 0.9}", SampleRateByService)

    then:
    sampleRate.getRate("test") == 0.8d
    sampleRate.getRate("another test") == 0.9d
  }

  def "test equals"() {
    when:
    EqualsVerifier.forClass(SampleRateByService).suppress(Warning.STRICT_INHERITANCE).verify()

    then:
    noExceptionThrown()
  }
}
