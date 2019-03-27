package datadog.trace.tracer.writer

import datadog.trace.tracer.Trace
import spock.lang.Retry
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit


class AgentWriterTest extends Specification {

  // Amount of time within with we expect flush to happen.
  // We make this slightly longer than flush time.
  private static final int FLUSH_DELAY = TimeUnit.SECONDS.toMillis(AgentWriter.FLUSH_TIME_SECONDS * 2)

  private static final AGENT_URL = new URL("http://example.com")

  def sampleRateByService = Mock(SampleRateByService)
  def client = Mock(AgentClient) {
    getAgentUrl() >> AGENT_URL
  }

  def "test happy path"() {
    setup:
    def incrementTraceCountBy = 5
    def traces = [
      Mock(Trace) {
        isValid() >> true
      },
      Mock(Trace) {
        isValid() >> false
      },
      Mock(Trace) {
        isValid() >> true
      }]
    def writer = new AgentWriter(client)

    when:
    for (def trace : traces) {
      writer.write(trace)
    }
    incrementTraceCountBy.times {
      writer.incrementTraceCount()
    }

    // Starting writer after submissions to make sure all updates go out in 1 request
    writer.start()

    Thread.sleep(FLUSH_DELAY)

    then:
    1 * client.sendTraces([traces[0], traces[2]], incrementTraceCountBy) >> sampleRateByService
    and:
    writer.getSampleRateByService() == sampleRateByService
    then:
    0 * client.sendTraces(_, _)

    cleanup:
    writer.close()
  }

  def "test small queue"() {
    setup:
    def traces = [
      Mock(Trace) {
        isValid() >> true
      },
      Mock(Trace) {
        isValid() >> true
      }]
    def writer = new AgentWriter(client, 1)

    when:
    for (def trace : traces) {
      writer.write(trace)
    }
    writer.start()
    Thread.sleep(FLUSH_DELAY)

    then:
    1 * client.sendTraces([traces[0]], 0)

    cleanup:
    writer.close()
  }

  def "test client exception handling"() {
    setup:
    def traces = [
      Mock(Trace) {
        isValid() >> true
      },
      Mock(Trace) {
        isValid() >> true
      }]
    def writer = new AgentWriter(client)
    writer.start()

    when:
    writer.write(traces[0])
    Thread.sleep(FLUSH_DELAY)

    then:
    1 * client.sendTraces([traces[0]], 0) >> { throw new IOException("test exception") }
    writer.getSampleRateByService() == SampleRateByService.EMPTY_INSTANCE

    when:
    writer.write(traces[1])
    Thread.sleep(FLUSH_DELAY)

    then:
    1 * client.sendTraces([traces[1]], 0) >> sampleRateByService
    writer.getSampleRateByService() == sampleRateByService

    cleanup:
    writer.close()
  }

  def "test agent url getter"() {
    setup:
    def writer = new AgentWriter(client)

    when:
    def agentUrl = writer.getAgentUrl()

    then:
    agentUrl == AGENT_URL
  }

  def "test default sample rate by service"() {
    setup:
    def writer = new AgentWriter(client)

    when:
    def sampleRateByService = writer.getSampleRateByService()

    then:
    sampleRateByService == SampleRateByService.EMPTY_INSTANCE
  }

  @Retry
  def "test start/#closeMethod"() {
    setup:
    def writer = new AgentWriter(client)

    expect:
    !isWriterThreadRunning()

    when:
    writer.start()

    then:
    isWriterThreadRunning()

    when:
    writer."${closeMethod}"()

    then:
    !isWriterThreadRunning()

    where:
    closeMethod | _
    "close"     | _
    "finalize"  | _
  }

  def "test shutdown callback"() {
    setup:
    def executor = Mock(ExecutorService) {
      awaitTermination(_, _) >> { throw new InterruptedException() }
    }
    def callback = new AgentWriter.ShutdownCallback(executor)

    when:
    callback.run()

    then:
    noExceptionThrown()
  }

  boolean isWriterThreadRunning() {
    // This is known to fail sometimes.
    return Thread.getAllStackTraces().keySet().any { t -> t.getName() == "dd-agent-writer" }
  }
}
