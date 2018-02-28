package datadog.trace.api.writer

import datadog.opentracing.DDSpan
import datadog.trace.common.writer.DDAgentWriter
import datadog.trace.common.writer.DDApi
import datadog.trace.common.writer.WriterQueue
import spock.lang.Specification
import spock.lang.Timeout

import static datadog.opentracing.SpanFactory.newSpanOf
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.verifyNoMoreInteractions

@Timeout(5)
class DDAgentWriterTest extends Specification {


  def "calls to the API are scheduled"() {

    setup:
    def api = Mock(DDApi)
    def writer = new DDAgentWriter(api)

    when:
    writer.start()
    Thread.sleep(flush_time_wait)

    then:
    0 * api.sendTraces(_ as List)

    when:
    for (def i = 0; i < tick; i++) {
      writer.write(trace)
      Thread.sleep(flush_time_wait)
    }

    then:
    tick * api.sendTraces([trace])

    where:
    trace = [newSpanOf(0)]
    flush_time_wait = (int) (1.2 * (DDAgentWriter.FLUSH_TIME_SECONDS * 1_000))
    tick << [1, 3]
  }

  def "check if trace has been added by force"() {

    setup:
    def traces = new WriterQueue<List<DDSpan>>(capacity)
    def writer = new DDAgentWriter(Mock(DDApi), traces)

    when:
    for (def i = 0; i < capacity; i++) {
      writer.write([])
    }

    then:
    traces.size() == capacity

    when:
    writer.write(trace)

    then:
    traces.size() == capacity
    traces.getAll().contains(trace)

    where:
    trace = [newSpanOf(0)]
    capacity = 10


  }

  def "check that are no interactions after close"() {

    setup:
    def api = mock(DDApi)
    def writer = new DDAgentWriter(api)
    writer.start()

    when:
    writer.close()
    writer.write([])
    Thread.sleep(flush_time_wait)

    then:
    verifyNoMoreInteractions(api)

    where:
    flush_time_wait = (int) (1.2 * (DDAgentWriter.FLUSH_TIME_SECONDS * 1_000))
  }
}
