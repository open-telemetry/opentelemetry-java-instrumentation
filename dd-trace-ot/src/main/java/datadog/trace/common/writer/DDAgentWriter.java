package datadog.trace.common.writer;

import static datadog.trace.api.Config.DEFAULT_AGENT_HOST;
import static datadog.trace.api.Config.DEFAULT_AGENT_UNIX_DOMAIN_SOCKET;
import static datadog.trace.api.Config.DEFAULT_TRACE_AGENT_PORT;
import static java.util.concurrent.TimeUnit.SECONDS;

import datadog.opentracing.DDSpan;
import datadog.trace.common.util.DaemonThreadFactory;
import datadog.trace.common.writer.ddagent.DDAgentApi;
import datadog.trace.common.writer.ddagent.DDAgentResponseListener;
import datadog.trace.common.writer.ddagent.Monitor;
import datadog.trace.common.writer.ddagent.TraceConsumer;
import datadog.trace.common.writer.ddagent.TraceSerializingDisruptor;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * This writer buffers traces and sends them to the provided DDApi instance.
 *
 * <p>Written traces are passed off to a disruptor so as to avoid blocking the application's thread.
 * If a flood of traces arrives that exceeds the disruptor ring size, the traces exceeding the
 * threshold will be counted and sampled.
 */
@Slf4j
public class DDAgentWriter implements Writer {
  private static final int DISRUPTOR_BUFFER_SIZE = 1024;
  private static final int SENDER_QUEUE_SIZE = 16;
  private static final int FLUSH_PAYLOAD_DELAY = 1; // 1/second

  private static final ThreadFactory SCHEDULED_FLUSH_THREAD_FACTORY =
      new DaemonThreadFactory("dd-trace-writer");

  private final DDAgentApi api;
  public final int flushFrequencySeconds;
  public final TraceSerializingDisruptor disruptor;

  public final ScheduledExecutorService scheduledWriterExecutor;
  private final AtomicInteger traceCount = new AtomicInteger(0);
  public final Phaser apiPhaser = new Phaser(); // Ensure API calls are completed when flushing;

  public final Monitor monitor;

  public DDAgentWriter() {
    this(
        new DDAgentApi(
            DEFAULT_AGENT_HOST, DEFAULT_TRACE_AGENT_PORT, DEFAULT_AGENT_UNIX_DOMAIN_SOCKET),
        new Monitor.Noop());
  }

  public DDAgentWriter(final DDAgentApi api, final Monitor monitor) {
    this(api, monitor, DISRUPTOR_BUFFER_SIZE, SENDER_QUEUE_SIZE, FLUSH_PAYLOAD_DELAY);
  }

  /** Old signature (pre-Monitor) used in tests */
  private DDAgentWriter(final DDAgentApi api) {
    this(api, new Monitor.Noop());
  }

  /**
   * Used in the tests.
   *
   * @param api
   * @param disruptorSize Rounded up to next power of 2
   * @param flushFrequencySeconds value < 1 disables scheduled flushes
   */
  private DDAgentWriter(
      final DDAgentApi api,
      final int disruptorSize,
      final int senderQueueSize,
      final int flushFrequencySeconds) {
    this(api, new Monitor.Noop(), disruptorSize, senderQueueSize, flushFrequencySeconds);
  }

  // DQH - TODO - Update the tests & remove this
  private DDAgentWriter(
      final DDAgentApi api,
      final Monitor monitor,
      final int disruptorSize,
      final int flushFrequencySeconds) {
    this(api, monitor, disruptorSize, SENDER_QUEUE_SIZE, flushFrequencySeconds);
  }

  // DQH - TODO - Update the tests & remove this
  private DDAgentWriter(
      final DDAgentApi api, final int disruptorSize, final int flushFrequencySeconds) {
    this(api, new Monitor.Noop(), disruptorSize, SENDER_QUEUE_SIZE, flushFrequencySeconds);
  }

  private DDAgentWriter(
      final DDAgentApi api,
      final Monitor monitor,
      final int disruptorSize,
      final int senderQueueSize,
      final int flushFrequencySeconds) {
    this.api = api;
    this.monitor = monitor;

    disruptor =
        new TraceSerializingDisruptor(
            disruptorSize, this, new TraceConsumer(traceCount, senderQueueSize, this));

    this.flushFrequencySeconds = flushFrequencySeconds;
    scheduledWriterExecutor = Executors.newScheduledThreadPool(1, SCHEDULED_FLUSH_THREAD_FACTORY);

    apiPhaser.register(); // Register on behalf of the scheduled executor thread.
  }

  public void addResponseListener(final DDAgentResponseListener listener) {
    api.addResponseListener(listener);
  }

  // Exposing some statistics for consumption by monitors
  public final long getDisruptorCapacity() {
    return disruptor.getDisruptorCapacity();
  }

  public final long getDisruptorUtilizedCapacity() {
    return getDisruptorCapacity() - getDisruptorRemainingCapacity();
  }

  public final long getDisruptorRemainingCapacity() {
    return disruptor.getDisruptorRemainingCapacity();
  }

  @Override
  public void write(final List<DDSpan> trace) {
    // We can't add events after shutdown otherwise it will never complete shutting down.
    if (disruptor.running) {
      final boolean published = disruptor.tryPublish(trace);

      if (published) {
        monitor.onPublish(DDAgentWriter.this, trace);
      } else {
        // We're discarding the trace, but we still want to count it.
        traceCount.incrementAndGet();
        log.debug("Trace written to overfilled buffer. Counted but dropping trace: {}", trace);

        monitor.onFailedPublish(this, trace);
      }
    } else {
      log.debug("Trace written after shutdown. Ignoring trace: {}", trace);

      monitor.onFailedPublish(this, trace);
    }
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
    disruptor.start();

    monitor.onStart(this);
  }

  @Override
  public void close() {

    boolean flushSuccess = true;

    // We have to shutdown scheduled executor first to make sure no flush events issued after
    // disruptor has been shutdown.
    // Otherwise those events will never be processed and flush call will wait forever.
    scheduledWriterExecutor.shutdown();
    try {
      scheduledWriterExecutor.awaitTermination(flushFrequencySeconds, SECONDS);
    } catch (final InterruptedException e) {
      log.warn("Waiting for flush executor shutdown interrupted.", e);

      flushSuccess = false;
    }
    flushSuccess |= disruptor.flush();

    disruptor.close();

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
