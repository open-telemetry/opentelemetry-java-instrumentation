package datadog.trace.common.writer;

import static datadog.trace.api.Config.DEFAULT_AGENT_HOST;
import static datadog.trace.api.Config.DEFAULT_AGENT_UNIX_DOMAIN_SOCKET;
import static datadog.trace.api.Config.DEFAULT_TRACE_AGENT_PORT;

import datadog.opentracing.DDSpan;
import datadog.trace.common.writer.ddagent.BatchWritingDisruptor;
import datadog.trace.common.writer.ddagent.DDAgentApi;
import datadog.trace.common.writer.ddagent.DDAgentResponseListener;
import datadog.trace.common.writer.ddagent.Monitor;
import datadog.trace.common.writer.ddagent.TraceProcessingDisruptor;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * This writer buffers traces and sends them to the provided DDApi instance. Buffering is done with
 * a distruptor to limit blocking the application threads. Internally, the trace is serialized and
 * put onto a separate disruptor that does block to decouple the CPU intensive from the IO bound
 * threads.
 *
 * <p>[Application] -> [trace processing buffer] -> [serialized trace batching buffer] -> [dd-agent]
 *
 * <p>Note: the first buffer is non-blocking and will discard if full, the second is blocking and
 * will cause back pressure on the trace processing (serializing) thread.
 *
 * <p>If the buffer is filled traces are discarded before serializing. Once serialized every effort
 * is made to keep, to avoid wasting the serialization effort.
 */
@Slf4j
public class DDAgentWriter implements Writer {
  @Value
  @lombok.Builder
  public static class Spec {
    @lombok.Builder.Default public String agentHost = DEFAULT_AGENT_HOST;
    @lombok.Builder.Default public int traceAgentPort = DEFAULT_TRACE_AGENT_PORT;
    @lombok.Builder.Default public String unixDomainSocket = DEFAULT_AGENT_UNIX_DOMAIN_SOCKET;
    @lombok.Builder.Default public int traceBufferSize = DISRUPTOR_BUFFER_SIZE;
    @lombok.Builder.Default public Monitor monitor = new Monitor.Noop();
    @lombok.Builder.Default public int flushFrequencySeconds = 1;
  }

  private static final int DISRUPTOR_BUFFER_SIZE = 1024;

  private final DDAgentApi api;
  private final TraceProcessingDisruptor traceProcessingDisruptor;
  private final BatchWritingDisruptor batchWritingDisruptor;

  private final AtomicInteger traceCount = new AtomicInteger(0);

  public final Monitor monitor;

  public DDAgentWriter() {
    this(Spec.builder().build());
  }

  public DDAgentWriter(final Spec spec) {
    api = new DDAgentApi(spec.agentHost, spec.traceAgentPort, spec.unixDomainSocket);
    monitor = spec.monitor;

    batchWritingDisruptor =
        new BatchWritingDisruptor(
            spec.traceBufferSize, spec.flushFrequencySeconds, api, monitor, this);
    traceProcessingDisruptor =
        new TraceProcessingDisruptor(
            spec.traceBufferSize, api, batchWritingDisruptor, monitor, this);
  }

  @Deprecated
  public DDAgentWriter(final DDAgentApi api, final Monitor monitor) {
    this.api = api;
    this.monitor = monitor;

    batchWritingDisruptor = new BatchWritingDisruptor(DISRUPTOR_BUFFER_SIZE, 1, api, monitor, this);
    traceProcessingDisruptor =
        new TraceProcessingDisruptor(
            DISRUPTOR_BUFFER_SIZE, api, batchWritingDisruptor, monitor, this);
  }

  @Deprecated
  // DQH - TODO - Update the tests & remove this
  private DDAgentWriter(
      final DDAgentApi api, final int disruptorSize, final int flushFrequencySeconds) {
    this.api = api;
    monitor = new Monitor.Noop();

    batchWritingDisruptor =
        new BatchWritingDisruptor(disruptorSize, flushFrequencySeconds, api, monitor, this);
    traceProcessingDisruptor =
        new TraceProcessingDisruptor(disruptorSize, api, batchWritingDisruptor, monitor, this);
  }

  public void addResponseListener(final DDAgentResponseListener listener) {
    api.addResponseListener(listener);
  }

  // Exposing some statistics for consumption by monitors
  public final long getDisruptorCapacity() {
    return traceProcessingDisruptor.getDisruptorCapacity();
  }

  public final long getDisruptorUtilizedCapacity() {
    return getDisruptorCapacity() - getDisruptorRemainingCapacity();
  }

  public final long getDisruptorRemainingCapacity() {
    return traceProcessingDisruptor.getDisruptorRemainingCapacity();
  }

  @Override
  public void write(final List<DDSpan> trace) {
    // We can't add events after shutdown otherwise it will never complete shutting down.
    if (traceProcessingDisruptor.running) {
      final int representativeCount = traceCount.getAndSet(0) + 1;
      final boolean published = traceProcessingDisruptor.publish(trace, representativeCount);

      if (published) {
        monitor.onPublish(DDAgentWriter.this, trace);
      } else {
        // We're discarding the trace, but we still want to count it.
        traceCount.addAndGet(representativeCount);
        log.debug("Trace written to overfilled buffer. Counted but dropping trace: {}", trace);

        monitor.onFailedPublish(this, trace);
      }
    } else {
      log.debug("Trace written after shutdown. Ignoring trace: {}", trace);

      monitor.onFailedPublish(this, trace);
    }
  }

  public boolean flush() {
    return traceProcessingDisruptor.flush(traceCount.getAndSet(0));
  }

  @Override
  public void incrementTraceCount() {
    traceCount.incrementAndGet();
  }

  public DDAgentApi getApi() {
    return api;
  }

  @Override
  public void start() {
    batchWritingDisruptor.start();
    traceProcessingDisruptor.start();
    monitor.onStart(this);
  }

  @Override
  public void close() {
    final boolean flushSuccess = traceProcessingDisruptor.flush(traceCount.getAndSet(0));
    try {
      traceProcessingDisruptor.close();
    } finally { // in case first close fails.
      batchWritingDisruptor.close();
    }
    monitor.onShutdown(this, flushSuccess);
  }

  @Override
  public String toString() {
    // DQH - I don't particularly like the instanceof check,
    // but I decided it was preferable to adding an isNoop method onto
    // Monitor or checking the result of Monitor#toString() to determine
    // if something is *probably* the NoopMonitor.

    String str = "DDAgentWriter { api=" + api;
    if (!(monitor instanceof Monitor.Noop)) {
      str += ", monitor=" + monitor;
    }
    str += " }";

    return str;
  }
}
