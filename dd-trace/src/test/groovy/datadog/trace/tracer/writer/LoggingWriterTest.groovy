package datadog.trace.tracer.writer

import datadog.trace.tracer.Trace
import spock.lang.Shared
import spock.lang.Specification

// TODO: this test set is incomplete, fill it in
class LoggingWriterTest extends Specification {

  @Shared
  def writer = new LoggingWriter()

  def "test start"() {
    when:
    writer.start()

    then:
    noExceptionThrown()
  }

  def "test close"() {
    when:
    writer.close()

    then:
    noExceptionThrown()
  }

  def "test write"() {
    setup:
    def trace = Mock(Trace)

    when:
    writer.write(trace)

    then:
    1 * trace.toString()
  }

  def "test getter"() {
    when:
    def sampleRateByInstance = writer.getSampleRateByService()

    then:
    sampleRateByInstance == SampleRateByService.EMPTY_INSTANCE
  }

}
