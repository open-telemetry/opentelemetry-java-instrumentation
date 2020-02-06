package datadog.trace.api.writer

import com.timgroup.statsd.StatsDClient
import datadog.opentracing.DDSpan
import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.DDAgentWriter
import datadog.trace.common.writer.ddagent.BatchWritingDisruptor
import datadog.trace.common.writer.ddagent.DDAgentApi
import datadog.trace.common.writer.ddagent.Monitor
import datadog.trace.util.test.DDSpecification
import org.msgpack.core.MessagePack
import org.msgpack.core.buffer.ArrayBufferOutput
import spock.lang.Retry
import spock.lang.Timeout

import java.util.concurrent.Phaser
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static datadog.opentracing.SpanFactory.newSpanOf
import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.common.serialization.MsgpackFormatWriter.MSGPACK_WRITER
import static datadog.trace.common.writer.DDAgentWriter.DISRUPTOR_BUFFER_SIZE

@Timeout(20)
class DDAgentWriterTest extends DDSpecification {

  def phaser = new Phaser()
  def api = Mock(DDAgentApi) {
    // Define the following response in the spec:
//    sendSerializedTraces(_, _, _) >> {
//      phaser.arrive()
//      return DDAgentApi.Response.success(200)
//    }
  }
  def monitor = Mock(Monitor)

  def setup() {
    // Register for two threads.
    phaser.register()
    phaser.register()
  }

  def "test happy path"() {
    setup:
    def writer = DDAgentWriter.builder().agentApi(api).traceBufferSize(2).flushFrequencySeconds(-1).build()
    writer.start()

    when:
    writer.flush()

    then:
    0 * _

    when:
    writer.write(trace)
    writer.write(trace)
    writer.flush()

    then:
    2 * api.serializeTrace(_) >> { trace -> callRealMethod() }
    1 * api.sendSerializedTraces(2, _, { it.size() == 2 }) >> DDAgentApi.Response.success(200)
    0 * _

    cleanup:
    writer.close()

    where:
    trace = [newSpanOf(0, "fixed-thread-name")]
  }

  def "test flood of traces"() {
    setup:
    def writer = DDAgentWriter.builder().agentApi(api).traceBufferSize(disruptorSize).flushFrequencySeconds(-1).build()
    writer.start()

    when:
    (1..traceCount).each {
      writer.write(trace)
    }
    writer.flush()

    then:
    _ * api.serializeTrace(_) >> { trace -> callRealMethod() }
    1 * api.sendSerializedTraces(traceCount, _, { it.size() < traceCount }) >> DDAgentApi.Response.success(200)
    0 * _

    cleanup:
    writer.close()

    where:
    trace = [newSpanOf(0, "fixed-thread-name")]
    disruptorSize = 2
    traceCount = 100 // Shouldn't trigger payload, but bigger than the disruptor size.
  }

  def "test flush by size"() {
    setup:
    def writer = DDAgentWriter.builder().agentApi(api).traceBufferSize(DISRUPTOR_BUFFER_SIZE).flushFrequencySeconds(-1).build()
    writer.start()

    when:
    (1..6).each {
      writer.write(trace)
    }
    // Wait for 2 flushes of 3 by size
    phaser.awaitAdvanceInterruptibly(phaser.arrive())
    phaser.awaitAdvanceInterruptibly(phaser.arriveAndDeregister())

    then:
    6 * api.serializeTrace(_) >> { trace -> callRealMethod() }
    2 * api.sendSerializedTraces(3, _, { it.size() == 3 }) >> {
      phaser.arrive()
      return DDAgentApi.Response.success(200)
    }

    when:
    (1..2).each {
      writer.write(trace)
    }
    // Flush the remaining 2
    writer.flush()

    then:
    2 * api.serializeTrace(_) >> { trace -> callRealMethod() }
    1 * api.sendSerializedTraces(2, _, { it.size() == 2 }) >> DDAgentApi.Response.success(200)
    0 * _

    cleanup:
    writer.close()

    where:
    span = newSpanOf(0, "fixed-thread-name")
    trace = (0..10000).collect { span }
  }

  def "test flush by time"() {
    setup:
    def writer = DDAgentWriter.builder().agentApi(api).monitor(monitor).build()
    writer.start()

    when:
    (1..5).each {
      writer.write(trace)
    }
    phaser.awaitAdvanceInterruptibly(phaser.arriveAndDeregister())

    then:
    5 * api.serializeTrace(_) >> { trace -> callRealMethod() }
    1 * api.sendSerializedTraces(5, _, { it.size() == 5 }) >> DDAgentApi.Response.success(200)
    5 * monitor.onPublish(_, _)
    5 * monitor.onSerialize(_, _, _)
    1 * monitor.onFlush(_, _)
    1 * monitor.onSend(_, _, _, _) >> {
      phaser.arrive()
    }
    0 * _

    cleanup:
    writer.close()

    where:
    span = newSpanOf(0, "fixed-thread-name")
    trace = (1..10).collect { span }
  }

  def "test default buffer size"() {
    setup:
    def writer = DDAgentWriter.builder().agentApi(api).traceBufferSize(DISRUPTOR_BUFFER_SIZE).flushFrequencySeconds(-1).build()
    writer.start()

    when:
    (0..maxedPayloadTraceCount).each {
      writer.write(minimalTrace)
      def start = System.nanoTime()
      // (consumer processes a trace in about 20 microseconds
      while (System.nanoTime() - start < TimeUnit.MICROSECONDS.toNanos(100)) {
        // Busywait because we don't want to fill up the ring buffer
      }
    }
    writer.flush()

    then:
    (maxedPayloadTraceCount + 1) * api.serializeTrace(_) >> { trace -> callRealMethod() }
    1 * api.sendSerializedTraces(maxedPayloadTraceCount, _, { it.size() == maxedPayloadTraceCount }) >> DDAgentApi.Response.success(200)

    cleanup:
    writer.close()

    where:
    minimalContext = new DDSpanContext(
      1G,
      1G,
      0G,
      "",
      "",
      "",
      PrioritySampling.UNSET,
      "",
      Collections.emptyMap(),
      false,
      "",
      Collections.emptyMap(),
      Mock(PendingTrace),
      Mock(DDTracer))
    minimalSpan = new DDSpan(0, minimalContext)
    minimalTrace = [minimalSpan]
    traceSize = calculateSize(minimalTrace)
    maxedPayloadTraceCount = ((int) (BatchWritingDisruptor.FLUSH_PAYLOAD_BYTES / traceSize)) + 1
  }

  def "check that are no interactions after close"() {
    setup:
    def writer = DDAgentWriter.builder().agentApi(api).monitor(monitor).build()
    writer.start()

    when:
    writer.close()
    writer.write([])
    writer.flush()

    then:
//    2 * monitor.onFlush(_, false)
    1 * monitor.onFailedPublish(_, _)
    1 * monitor.onShutdown(_, _)
    0 * _
    writer.traceCount.get() == 0
  }

  def "check shutdown if batchWritingDisruptor stopped first"() {
    setup:
    def writer = DDAgentWriter.builder().agentApi(api).monitor(monitor).build()
    writer.start()
    writer.batchWritingDisruptor.close()

    when:
    writer.write([])
    writer.flush()

    then:
    1 * api.serializeTrace(_) >> { trace -> callRealMethod() }
    1 * monitor.onSerialize(writer, _, _)
    1 * monitor.onPublish(writer, _)
    0 * _
    writer.traceCount.get() == 0

    cleanup:
    writer.close()
  }

  def createMinimalTrace() {
    def minimalContext = new DDSpanContext(
      1G,
      1G,
      0G,
      "",
      "",
      "",
      PrioritySampling.UNSET,
      "",
      Collections.emptyMap(),
      false,
      "",
      Collections.emptyMap(),
      Mock(PendingTrace),
      Mock(DDTracer))
    def minimalSpan = new DDSpan(0, minimalContext)
    def minimalTrace = [minimalSpan]

    return minimalTrace
  }

  def "monitor happy path"() {
    setup:
    def minimalTrace = createMinimalTrace()

    // DQH -- need to set-up a dummy agent for the final send callback to work
    def agent = httpServer {
      handlers {
        put("v0.4/traces") {
          response.status(200).send()
        }
      }
    }
    def writer = DDAgentWriter.builder().traceAgentPort(agent.address.port).monitor(monitor).build()

    when:
    writer.start()

    then:
    1 * monitor.onStart(writer)

    when:
    writer.write(minimalTrace)
    writer.flush()

    then:
    1 * monitor.onPublish(writer, minimalTrace)
    1 * monitor.onSerialize(writer, minimalTrace, _)
    1 * monitor.onFlush(writer, _)
    1 * monitor.onSend(writer, 1, _, { response -> response.success() && response.status() == 200 })

    when:
    writer.close()

    then:
    1 * monitor.onShutdown(writer, true)

    cleanup:
    agent.close()
  }

  def "monitor agent returns error"() {
    setup:
    def minimalTrace = createMinimalTrace()

    // DQH -- need to set-up a dummy agent for the final send callback to work
    def first = true
    def agent = httpServer {
      handlers {
        put("v0.4/traces") {
          // DQH - DDApi sniffs for end point existence, so respond with 200 the first time
          if (first) {
            response.status(200).send()
            first = false
          } else {
            response.status(500).send()
          }
        }
      }
    }
    def writer = DDAgentWriter.builder().traceAgentPort(agent.address.port).monitor(monitor).build()

    when:
    writer.start()

    then:
    1 * monitor.onStart(writer)

    when:
    writer.write(minimalTrace)
    writer.flush()

    then:
    1 * monitor.onPublish(writer, minimalTrace)
    1 * monitor.onSerialize(writer, minimalTrace, _)
    1 * monitor.onFlush(writer, _)
    1 * monitor.onFailedSend(writer, 1, _, { response -> !response.success() && response.status() == 500 })

    when:
    writer.close()

    then:
    1 * monitor.onShutdown(writer, true)

    cleanup:
    agent.close()
  }

  def "unreachable agent test"() {
    setup:
    def minimalTrace = createMinimalTrace()

    def api = new DDAgentApi("localhost", 8192, null) {
      DDAgentApi.Response sendSerializedTraces(
        int representativeCount,
        Integer sizeInBytes,
        List<byte[]> traces) {
        // simulating a communication failure to a server
        return DDAgentApi.Response.failed(new IOException("comm error"))
      }
    }
    def writer = DDAgentWriter.builder().agentApi(api).monitor(monitor).build()

    when:
    writer.start()

    then:
    1 * monitor.onStart(writer)

    when:
    writer.write(minimalTrace)
    writer.flush()

    then:
    1 * monitor.onPublish(writer, minimalTrace)
    1 * monitor.onSerialize(writer, minimalTrace, _)
    1 * monitor.onFlush(writer, _)
    1 * monitor.onFailedSend(writer, 1, _, { response -> !response.success() && response.status() == null })

    when:
    writer.close()

    then:
    1 * monitor.onShutdown(writer, true)
  }

  @Retry(delay = 10)
  // if execution is too slow, the http client timeout may trigger.
  def "slow response test"() {
    def numWritten = 0
    def numFlushes = new AtomicInteger(0)
    def numPublished = new AtomicInteger(0)
    def numFailedPublish = new AtomicInteger(0)
    def numRequests = new AtomicInteger(0)
    def numFailedRequests = new AtomicInteger(0)

    def responseSemaphore = new Semaphore(1)

    setup:

    // Need to set-up a dummy agent for the final send callback to work
    def agent = httpServer {
      handlers {
        put("v0.4/traces") {
          // DDApi sniffs for end point existence, so respond quickly the first time
          // then slowly thereafter

          responseSemaphore.acquire()
          try {
            response.status(200).send()
          } finally {
            responseSemaphore.release()
          }
        }
      }
    }

    // This test focuses just on failed publish, so not verifying every callback
    def monitor = Stub(Monitor) {
      onPublish(_, _) >> {
        numPublished.incrementAndGet()
      }
      onFailedPublish(_, _) >> {
        numFailedPublish.incrementAndGet()
      }
      onFlush(_, _) >> {
        numFlushes.incrementAndGet()
      }
      onSend(_, _, _, _) >> {
        numRequests.incrementAndGet()
      }
      onFailedPublish(_, _, _, _) >> {
        numFailedRequests.incrementAndGet()
      }
    }

    def writer = DDAgentWriter.builder().traceAgentPort(agent.address.port).monitor(monitor).traceBufferSize(bufferSize).build()
    writer.start()

    // gate responses
    responseSemaphore.acquire()

    when:
    // write a single trace and flush
    // with responseSemaphore held, the response is blocked but may still time out
    writer.write(minimalTrace)
    numWritten += 1

    // sanity check coordination mechanism of test
    // release to allow response to be generated
    responseSemaphore.release()
    writer.flush()

    // reacquire semaphore to stall further responses
    responseSemaphore.acquire()

    then:
    numFailedPublish.get() == 0
    numPublished.get() == numWritten
    numPublished.get() + numFailedPublish.get() == numWritten
    numFlushes.get() == 1

    when:
    // send many traces to fill the sender queue...
    //   loop until outstanding requests > finished requests
    while (writer.traceProcessingDisruptor.disruptorRemainingCapacity + writer.batchWritingDisruptor.disruptorRemainingCapacity > 0 || numFailedPublish.get() == 0) {
      writer.write(minimalTrace)
      numWritten += 1
      Thread.sleep(1) // Allow traces to get serialized.
    }

    then:
    numFailedPublish.get() > 0
    numPublished.get() + numFailedPublish.get() == numWritten

    when:
    def priorNumFailed = numFailedPublish.get()

    // with both disruptor & queue full, should reject everything
    def expectedRejects = 100
    (1..expectedRejects).each {
      writer.write(minimalTrace)
      numWritten += 1
    }

    then:
    // If the in-flight request times out (we don't currently retry),
    // then a new batch will begin processing and many of traces will
    // be accepted and batched into a new failing request.
    // In that case, the reject number will be low.
    numFailedPublish.get() - priorNumFailed >= expectedRejects * 0.80
    numPublished.get() + numFailedPublish.get() == numWritten

    cleanup:
    responseSemaphore.release()

    writer.close()
    agent.close()

    where:
    bufferSize = 16
    minimalTrace = createMinimalTrace()
  }

  def "multi threaded"() {
    def numPublished = new AtomicInteger(0)
    def numFailedPublish = new AtomicInteger(0)
    def numRepSent = new AtomicInteger(0)

    setup:
    def minimalTrace = createMinimalTrace()

    // Need to set-up a dummy agent for the final send callback to work
    def agent = httpServer {
      handlers {
        put("v0.4/traces") {
          response.status(200).send()
        }
      }
    }

    // This test focuses just on failed publish, so not verifying every callback
    def monitor = Stub(Monitor) {
      onPublish(_, _) >> {
        numPublished.incrementAndGet()
      }
      onFailedPublish(_, _) >> {
        numFailedPublish.incrementAndGet()
      }
      onSend(_, _, _, _) >> { writer, repCount, sizeInBytes, response ->
        numRepSent.addAndGet(repCount)
      }
    }

    def writer = DDAgentWriter.builder().traceAgentPort(agent.address.port).monitor(monitor).build()
    writer.start()

    when:
    def producer = {
      (1..100).each {
        writer.write(minimalTrace)
      }
    } as Runnable

    def t1 = new Thread(producer)
    t1.start()

    def t2 = new Thread(producer)
    t2.start()

    t1.join()
    t2.join()

    writer.flush()

    then:
    def totalTraces = 100 + 100
    numPublished.get() == totalTraces
    numRepSent.get() == totalTraces

    cleanup:
    writer.close()
    agent.close()
  }

  def "statsd success"() {
    def numTracesAccepted = 0
    def numRequests = 0
    def numResponses = 0

    setup:
    def minimalTrace = createMinimalTrace()

    // Need to set-up a dummy agent for the final send callback to work
    def agent = httpServer {
      handlers {
        put("v0.4/traces") {
          response.status(200).send()
        }
      }
    }

    def statsd = Stub(StatsDClient)
    statsd.incrementCounter("queue.accepted") >> { stat ->
      numTracesAccepted += 1
    }
    statsd.incrementCounter("api.requests") >> { stat ->
      numRequests += 1
    }
    statsd.incrementCounter("api.responses", _) >> { stat, tags ->
      numResponses += 1
    }

    def monitor = new Monitor.StatsD(statsd)
    def writer = DDAgentWriter.builder().traceAgentPort(agent.address.port).monitor(monitor).build()
    writer.start()

    when:
    writer.write(minimalTrace)
    writer.flush()

    then:
    numTracesAccepted == 1
    numRequests == 1
    numResponses == 1

    cleanup:
    agent.close()
    writer.close()
  }

  def "statsd comm failure"() {
    def numRequests = 0
    def numResponses = 0
    def numErrors = 0

    setup:
    def minimalTrace = createMinimalTrace()

    // DQH -- need to set-up a dummy agent for the final send callback to work
    def api = new DDAgentApi("localhost", 8192, null) {
      DDAgentApi.Response sendSerializedTraces(
        int representativeCount,
        Integer sizeInBytes,
        List<byte[]> traces) {
        // simulating a communication failure to a server
        return DDAgentApi.Response.failed(new IOException("comm error"))
      }
    }

    def statsd = Stub(StatsDClient)
    statsd.incrementCounter("api.requests") >> { stat ->
      numRequests += 1
    }
    statsd.incrementCounter("api.responses", _) >> { stat, tags ->
      numResponses += 1
    }
    statsd.incrementCounter("api.errors", _) >> { stat ->
      numErrors += 1
    }

    def monitor = new Monitor.StatsD(statsd)
    def writer = DDAgentWriter.builder().agentApi(api).monitor(monitor).build()
    writer.start()

    when:
    writer.write(minimalTrace)
    writer.flush()

    then:
    numRequests == 1
    numResponses == 0
    numErrors == 1

    cleanup:
    writer.close()
  }

  static int calculateSize(List<DDSpan> trace) {
    def buffer = new ArrayBufferOutput()
    def packer = MessagePack.newDefaultPacker(buffer)
    MSGPACK_WRITER.writeTrace(trace, packer)
    packer.flush()
    return buffer.size
  }
}
