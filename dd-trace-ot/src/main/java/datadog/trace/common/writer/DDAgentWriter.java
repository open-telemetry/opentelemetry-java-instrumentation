package datadog.trace.common.writer;

import static datadog.trace.api.Config.DEFAULT_AGENT_HOST;
import static datadog.trace.api.Config.DEFAULT_AGENT_UNIX_DOMAIN_SOCKET;
import static datadog.trace.api.Config.DEFAULT_TRACE_AGENT_PORT;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import datadog.opentracing.DDSpan;
import datadog.opentracing.DDTraceOTInfo;
import datadog.trace.common.util.DaemonThreadFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
  private static final int FLUSH_PAYLOAD_BYTES = 5_000_000; // 5 MB
  private static final int FLUSH_PAYLOAD_DELAY = 1; // 1/second

  private static final EventTranslatorOneArg<Event<List<DDSpan>>, List<DDSpan>> TRANSLATOR =
      new EventTranslatorOneArg<Event<List<DDSpan>>, List<DDSpan>>() {
        @Override
        public void translateTo(
            final Event<List<DDSpan>> event, final long sequence, final List<DDSpan> trace) {
          event.data = trace;
        }
      };
  private static final EventTranslator<Event<List<DDSpan>>> FLUSH_TRANSLATOR =
      new EventTranslator<Event<List<DDSpan>>>() {
        @Override
        public void translateTo(final Event<List<DDSpan>> event, final long sequence) {
          event.shouldFlush = true;
        }
      };

  private static final ThreadFactory DISRUPTOR_THREAD_FACTORY =
      new DaemonThreadFactory("dd-trace-disruptor");
  private static final ThreadFactory SCHEDULED_FLUSH_THREAD_FACTORY =
      new DaemonThreadFactory("dd-trace-writer");

  private final Runnable flushTask = new FlushTask();
  private final DDApi api;
  private final int flushFrequencySeconds;
  private final Disruptor<Event<List<DDSpan>>> disruptor;

  private final Semaphore senderSemaphore;
  private final ScheduledExecutorService scheduledWriterExecutor;
  private final AtomicInteger traceCount = new AtomicInteger(0);
  private final AtomicReference<ScheduledFuture<?>> flushSchedule = new AtomicReference<>();
  private final Phaser apiPhaser;
  private volatile boolean running = false;

  private final Monitor monitor;

  public DDAgentWriter() {
    this(
        new DDApi(DEFAULT_AGENT_HOST, DEFAULT_TRACE_AGENT_PORT, DEFAULT_AGENT_UNIX_DOMAIN_SOCKET),
        new NoopMonitor());
  }

  public DDAgentWriter(final DDApi api, final Monitor monitor) {
    this(api, monitor, DISRUPTOR_BUFFER_SIZE, SENDER_QUEUE_SIZE, FLUSH_PAYLOAD_DELAY);
  }

  /** Old signature (pre-Monitor) used in tests */
  private DDAgentWriter(final DDApi api) {
    this(api, new NoopMonitor());
  }

  /**
   * Used in the tests.
   *
   * @param api
   * @param disruptorSize Rounded up to next power of 2
   * @param flushFrequencySeconds value < 1 disables scheduled flushes
   */
  private DDAgentWriter(
      final DDApi api,
      final int disruptorSize,
      final int senderQueueSize,
      final int flushFrequencySeconds) {
    this(api, new NoopMonitor(), disruptorSize, senderQueueSize, flushFrequencySeconds);
  }

  // DQH - TODO - Update the tests & remove this
  private DDAgentWriter(
      final DDApi api,
      final Monitor monitor,
      final int disruptorSize,
      final int flushFrequencySeconds) {
    this(api, monitor, disruptorSize, SENDER_QUEUE_SIZE, flushFrequencySeconds);
  }

  // DQH - TODO - Update the tests & remove this
  private DDAgentWriter(final DDApi api, final int disruptorSize, final int flushFrequencySeconds) {
    this(api, new NoopMonitor(), disruptorSize, SENDER_QUEUE_SIZE, flushFrequencySeconds);
  }

  private DDAgentWriter(
      final DDApi api,
      final Monitor monitor,
      final int disruptorSize,
      final int senderQueueSize,
      final int flushFrequencySeconds) {
    this.api = api;
    this.monitor = monitor;

    disruptor =
        new Disruptor<>(
            new DisruptorEventFactory<List<DDSpan>>(),
            Math.max(2, Integer.highestOneBit(disruptorSize - 1) << 1), // Next power of 2
            DISRUPTOR_THREAD_FACTORY,
            ProducerType.MULTI,
            new SleepingWaitStrategy(0, TimeUnit.MILLISECONDS.toNanos(5)));
    disruptor.handleEventsWith(new TraceConsumer());

    this.flushFrequencySeconds = flushFrequencySeconds;
    senderSemaphore = new Semaphore(senderQueueSize);
    scheduledWriterExecutor = Executors.newScheduledThreadPool(1, SCHEDULED_FLUSH_THREAD_FACTORY);

    apiPhaser = new Phaser(); // Ensure API calls are completed when flushing
    apiPhaser.register(); // Register on behalf of the scheduled executor thread.
  }

  // Exposing some statistics for consumption by monitors
  public final long getDisruptorCapacity() {
    return disruptor.getRingBuffer().getBufferSize();
  }

  public final long getDisruptorUtilizedCapacity() {
    return getDisruptorCapacity() - getDisruptorRemainingCapacity();
  }

  public final long getDisruptorRemainingCapacity() {
    return disruptor.getRingBuffer().remainingCapacity();
  }

  @Override
  public void write(final List<DDSpan> trace) {
    // We can't add events after shutdown otherwise it will never complete shutting down.
    if (running) {
      final boolean published = disruptor.getRingBuffer().tryPublishEvent(TRANSLATOR, trace);

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

  public DDApi getApi() {
    return api;
  }

  @Override
  public void start() {
    disruptor.start();
    running = true;
    scheduleFlush();

    monitor.onStart(this);
  }

  @Override
  public void close() {
    running = false;

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
    flushSuccess |= flush();
    disruptor.shutdown();

    monitor.onShutdown(this, flushSuccess);
  }

  /** This method will block until the flush is complete. */
  public boolean flush() {
    if (running) {
      log.info("Flushing any remaining traces.");
      // Register with the phaser so we can block until the flush completion.
      apiPhaser.register();
      disruptor.publishEvent(FLUSH_TRANSLATOR);
      try {
        // Allow thread to be interrupted.
        apiPhaser.awaitAdvanceInterruptibly(apiPhaser.arriveAndDeregister());

        return true;
      } catch (final InterruptedException e) {
        log.warn("Waiting for flush interrupted.", e);

        return false;
      }
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    // DQH - I don't particularly like the instanceof check,
    // but I decided it was preferable to adding an isNoop method onto
    // Monitor or checking the result of Monitor#toString() to determine
    // if something is *probably* the NoopMonitor.

    String str = "DDAgentWriter { api=" + api;
    if (!(monitor instanceof NoopMonitor)) {
      str += ", monitor=" + monitor;
    }
    str += " }";

    return str;
  }

  private void scheduleFlush() {
    if (flushFrequencySeconds > 0 && !scheduledWriterExecutor.isShutdown()) {
      final ScheduledFuture<?> previous =
          flushSchedule.getAndSet(
              scheduledWriterExecutor.schedule(flushTask, flushFrequencySeconds, SECONDS));

      final boolean previousIncomplete = (previous != null);
      if (previousIncomplete) {
        previous.cancel(true);
      }

      monitor.onScheduleFlush(this, previousIncomplete);
    }
  }

  private class FlushTask implements Runnable {
    @Override
    public void run() {
      // Don't call flush() because it would block the thread also used for sending the traces.
      disruptor.publishEvent(FLUSH_TRANSLATOR);
    }
  }

  /** This class is intentionally not threadsafe. */
  private class TraceConsumer implements EventHandler<Event<List<DDSpan>>> {
    private List<byte[]> serializedTraces = new ArrayList<>();
    private int payloadSize = 0;

    @Override
    public void onEvent(
        final Event<List<DDSpan>> event, final long sequence, final boolean endOfBatch) {
      final List<DDSpan> trace = event.data;
      event.data = null; // clear the event for reuse.
      if (trace != null) {
        traceCount.incrementAndGet();
        try {
          final byte[] serializedTrace = api.serializeTrace(trace);
          payloadSize += serializedTrace.length;
          serializedTraces.add(serializedTrace);

          monitor.onSerialize(DDAgentWriter.this, trace, serializedTrace);
        } catch (final JsonProcessingException e) {
          log.warn("Error serializing trace", e);

          monitor.onFailedSerialize(DDAgentWriter.this, trace, e);
        } catch (final Throwable e) {
          log.debug("Error while serializing trace", e);

          monitor.onFailedSerialize(DDAgentWriter.this, trace, e);
        }
      }

      if (event.shouldFlush || payloadSize >= FLUSH_PAYLOAD_BYTES) {
        boolean early = (payloadSize >= FLUSH_PAYLOAD_BYTES);

        reportTraces(early);
        event.shouldFlush = false;
      }
    }

    private void reportTraces(final boolean early) {
      try {
        if (serializedTraces.isEmpty()) {
          monitor.onFlush(DDAgentWriter.this, early);

          apiPhaser.arrive(); // Allow flush to return
          return;
          // scheduleFlush called in finally block.
        }
        final List<byte[]> toSend = serializedTraces;
        serializedTraces = new ArrayList<>(toSend.size());
        // ^ Initialize with similar size to reduce arraycopy churn.

        final int representativeCount = traceCount.getAndSet(0);
        final int sizeInBytes = payloadSize;

        monitor.onFlush(DDAgentWriter.this, early);

        // Run the actual IO task on a different thread to avoid blocking the consumer.
        try {
          senderSemaphore.acquire();
        } catch (final InterruptedException e) {
          monitor.onFailedSend(
              DDAgentWriter.this, representativeCount, sizeInBytes, DDApi.Response.failed(e));

          // Finally, we'll schedule another flush
          // Any threads awaiting the flush will continue to wait
          return;
        }
        scheduledWriterExecutor.execute(
            new Runnable() {
              @Override
              public void run() {
                senderSemaphore.release();

                try {
                  final DDApi.Response response =
                      api.sendSerializedTraces(representativeCount, sizeInBytes, toSend);

                  if (response.success()) {
                    log.debug("Successfully sent {} traces to the API", toSend.size());

                    monitor.onSend(DDAgentWriter.this, representativeCount, sizeInBytes, response);
                  } else {
                    log.debug(
                        "Failed to send {} traces (representing {}) of size {} bytes to the API",
                        toSend.size(),
                        representativeCount,
                        sizeInBytes);

                    monitor.onFailedSend(
                        DDAgentWriter.this, representativeCount, sizeInBytes, response);
                  }
                } catch (final Throwable e) {
                  log.debug("Failed to send traces to the API: {}", e.getMessage());

                  // DQH - 10/2019 - DDApi should wrap most exceptions itself, so this really
                  // shouldn't occur.
                  // However, just to be safe to start, create a failed Response to handle any
                  // spurious Throwable-s.
                  monitor.onFailedSend(
                      DDAgentWriter.this,
                      representativeCount,
                      sizeInBytes,
                      DDApi.Response.failed(e));
                } finally {
                  apiPhaser.arrive(); // Flush completed.
                }
              }
            });
      } finally {
        payloadSize = 0;
        scheduleFlush();
      }
    }
  }

  private static class Event<T> {
    private volatile boolean shouldFlush = false;
    private volatile T data = null;
  }

  private static class DisruptorEventFactory<T> implements EventFactory<Event<T>> {
    @Override
    public Event<T> newInstance() {
      return new Event<>();
    }
  }

  /**
   * Callback interface for monitoring the health of the DDAgentWriter. Provides hooks for major
   * lifecycle events...
   *
   * <ul>
   *   <li>start
   *   <li>shutdown
   *   <li>publishing to disruptor
   *   <li>serializing
   *   <li>sending to agent
   * </ul>
   */
  public interface Monitor {
    void onStart(final DDAgentWriter agentWriter);

    void onShutdown(final DDAgentWriter agentWriter, final boolean flushSuccess);

    void onPublish(final DDAgentWriter agentWriter, final List<DDSpan> trace);

    void onFailedPublish(final DDAgentWriter agentWriter, final List<DDSpan> trace);

    void onFlush(final DDAgentWriter agentWriter, final boolean early);

    void onScheduleFlush(final DDAgentWriter agentWriter, final boolean previousIncomplete);

    void onSerialize(
        final DDAgentWriter agentWriter, final List<DDSpan> trace, final byte[] serializedTrace);

    void onFailedSerialize(
        final DDAgentWriter agentWriter, final List<DDSpan> trace, final Throwable optionalCause);

    void onSend(
        final DDAgentWriter agentWriter,
        final int representativeCount,
        final int sizeInBytes,
        final DDApi.Response response);

    void onFailedSend(
        final DDAgentWriter agentWriter,
        final int representativeCount,
        final int sizeInBytes,
        final DDApi.Response response);
  }

  public static final class NoopMonitor implements Monitor {
    @Override
    public void onStart(final DDAgentWriter agentWriter) {}

    @Override
    public void onShutdown(final DDAgentWriter agentWriter, final boolean flushSuccess) {}

    @Override
    public void onPublish(final DDAgentWriter agentWriter, final List<DDSpan> trace) {}

    @Override
    public void onFailedPublish(final DDAgentWriter agentWriter, final List<DDSpan> trace) {}

    @Override
    public void onFlush(final DDAgentWriter agentWriter, final boolean early) {}

    @Override
    public void onScheduleFlush(
        final DDAgentWriter agentWriter, final boolean previousIncomplete) {}

    @Override
    public void onSerialize(
        final DDAgentWriter agentWriter, final List<DDSpan> trace, final byte[] serializedTrace) {}

    @Override
    public void onFailedSerialize(
        final DDAgentWriter agentWriter, final List<DDSpan> trace, final Throwable optionalCause) {}

    @Override
    public void onSend(
        final DDAgentWriter agentWriter,
        final int representativeCount,
        final int sizeInBytes,
        final DDApi.Response response) {}

    @Override
    public void onFailedSend(
        final DDAgentWriter agentWriter,
        final int representativeCount,
        final int sizeInBytes,
        final DDApi.Response response) {}

    @Override
    public String toString() {
      return "NoOp";
    }
  }

  public static final class StatsDMonitor implements Monitor {
    public static final String PREFIX = "datadog.tracer";

    public static final String LANG_TAG = "lang";
    public static final String LANG_VERSION_TAG = "lang_version";
    public static final String LANG_INTERPRETER_TAG = "lang_interpreter";
    public static final String LANG_INTERPRETER_VENDOR_TAG = "lang_interpreter_vendor";
    public static final String TRACER_VERSION_TAG = "tracer_version";

    private final String hostInfo;
    private final StatsDClient statsd;

    // DQH - Made a conscious choice to not take a Config object here.
    // Letting the creating of the Monitor take the Config,
    // so it can decide which Monitor variant to create.
    public StatsDMonitor(final String host, final int port) {
      hostInfo = host + ":" + port;
      statsd = new NonBlockingStatsDClient(PREFIX, host, port, getDefaultTags());
    }

    // Currently, intended for testing
    private StatsDMonitor(final StatsDClient statsd) {
      hostInfo = null;
      this.statsd = statsd;
    }

    protected static final String[] getDefaultTags() {
      return new String[] {
        tag(LANG_TAG, "java"),
        tag(LANG_VERSION_TAG, DDTraceOTInfo.JAVA_VERSION),
        tag(LANG_INTERPRETER_TAG, DDTraceOTInfo.JAVA_VM_NAME),
        tag(LANG_INTERPRETER_VENDOR_TAG, DDTraceOTInfo.JAVA_VM_VENDOR),
        tag(TRACER_VERSION_TAG, DDTraceOTInfo.VERSION)
      };
    }

    private static final String tag(final String tagPrefix, final String tagValue) {
      return tagPrefix + ":" + tagValue;
    }

    @Override
    public void onStart(final DDAgentWriter agentWriter) {
      statsd.recordGaugeValue("queue.max_length", agentWriter.getDisruptorCapacity());
    }

    @Override
    public void onShutdown(final DDAgentWriter agentWriter, final boolean flushSuccess) {}

    @Override
    public void onPublish(final DDAgentWriter agentWriter, final List<DDSpan> trace) {
      statsd.incrementCounter("queue.accepted");
      statsd.count("queue.accepted_lengths", trace.size());
    }

    @Override
    public void onFailedPublish(final DDAgentWriter agentWriter, final List<DDSpan> trace) {
      statsd.incrementCounter("queue.dropped");
    }

    @Override
    public void onScheduleFlush(final DDAgentWriter agentWriter, final boolean previousIncomplete) {
      // not recorded
    }

    @Override
    public void onFlush(final DDAgentWriter agentWriter, final boolean early) {}

    @Override
    public void onSerialize(
        final DDAgentWriter agentWriter, final List<DDSpan> trace, final byte[] serializedTrace) {
      // DQH - Because of Java tracer's 2 phase acceptance and serialization scheme, this doesn't
      // map precisely
      statsd.count("queue.accepted_size", serializedTrace.length);
    }

    @Override
    public void onFailedSerialize(
        final DDAgentWriter agentWriter, final List<DDSpan> trace, final Throwable optionalCause) {
      // TODO - DQH - make a new stat for serialization failure -- or maybe count this towards
      // api.errors???
    }

    @Override
    public void onSend(
        final DDAgentWriter agentWriter,
        final int representativeCount,
        final int sizeInBytes,
        final DDApi.Response response) {
      onSendAttempt(agentWriter, representativeCount, sizeInBytes, response);
    }

    @Override
    public void onFailedSend(
        final DDAgentWriter agentWriter,
        final int representativeCount,
        final int sizeInBytes,
        final DDApi.Response response) {
      onSendAttempt(agentWriter, representativeCount, sizeInBytes, response);
    }

    private void onSendAttempt(
        final DDAgentWriter agentWriter,
        final int representativeCount,
        final int sizeInBytes,
        final DDApi.Response response) {
      statsd.incrementCounter("api.requests");
      statsd.recordGaugeValue("queue.length", representativeCount);
      // TODO: missing queue.spans (# of spans being sent)
      statsd.recordGaugeValue("queue.size", sizeInBytes);

      if (response.exception() != null) {
        // covers communication errors -- both not receiving a response or
        // receiving malformed response (even when otherwise successful)
        statsd.incrementCounter("api.errors");
      }

      if (response.status() != null) {
        statsd.incrementCounter("api.responses", "status: " + response.status());
      }
    }

    public String toString() {
      if (hostInfo == null) {
        return "StatsD";
      } else {
        return "StatsD { host=" + hostInfo + " }";
      }
    }
  }
}
