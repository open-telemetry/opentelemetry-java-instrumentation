package datadog.trace.common.writer.ddagent;

import static datadog.trace.common.writer.ddagent.DisruptorEvent.FlushTranslator.FLUSH_TRANSLATOR;
import static datadog.trace.common.writer.ddagent.DisruptorEvent.TraceTranslator.TRACE_TRANSLATOR;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import datadog.opentracing.DDSpan;
import datadog.trace.common.util.DaemonThreadFactory;
import datadog.trace.common.writer.DDAgentWriter;
import java.io.Closeable;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TraceSerializingDisruptor implements Closeable {
  private static final ThreadFactory DISRUPTOR_THREAD_FACTORY =
      new DaemonThreadFactory("dd-trace-disruptor");
  private final FlushTask flushTask = new FlushTask();

  private final Disruptor<DisruptorEvent<List<DDSpan>>> disruptor;
  private final DDAgentWriter writer;

  public volatile boolean running = false;

  private final AtomicReference<ScheduledFuture<?>> flushSchedule = new AtomicReference<>();

  public TraceSerializingDisruptor(
      final int disruptorSize, final DDAgentWriter writer, final TraceConsumer handler) {
    disruptor =
        new Disruptor<>(
            new DisruptorEvent.Factory<List<DDSpan>>(),
            Math.max(2, Integer.highestOneBit(disruptorSize - 1) << 1), // Next power of 2
            DISRUPTOR_THREAD_FACTORY,
            ProducerType.MULTI,
            new SleepingWaitStrategy(0, TimeUnit.MILLISECONDS.toNanos(5)));
    this.writer = writer;
    disruptor.handleEventsWith(handler);
  }

  public void start() {
    disruptor.start();
    running = true;
    scheduleFlush();
  }

  @Override
  public void close() {
    running = false;
    disruptor.shutdown();
  }

  public boolean tryPublish(final List<DDSpan> trace) {
    return disruptor.getRingBuffer().tryPublishEvent(TRACE_TRANSLATOR, trace);
  }

  /** This method will block until the flush is complete. */
  public boolean flush() {
    if (running) {
      log.info("Flushing any remaining traces.");
      // Register with the phaser so we can block until the flush completion.
      writer.apiPhaser.register();
      disruptor.publishEvent(FLUSH_TRANSLATOR);
      try {
        // Allow thread to be interrupted.
        writer.apiPhaser.awaitAdvanceInterruptibly(writer.apiPhaser.arriveAndDeregister());

        return true;
      } catch (final InterruptedException e) {
        log.warn("Waiting for flush interrupted.", e);

        return false;
      }
    } else {
      return false;
    }
  }

  public void scheduleFlush() {
    if (writer.flushFrequencySeconds > 0 && !writer.scheduledWriterExecutor.isShutdown()) {
      final ScheduledFuture<?> previous =
          flushSchedule.getAndSet(
              writer.scheduledWriterExecutor.schedule(
                  flushTask, writer.flushFrequencySeconds, SECONDS));

      final boolean previousIncomplete = (previous != null);
      if (previousIncomplete) {
        previous.cancel(true);
      }

      writer.monitor.onScheduleFlush(writer, previousIncomplete);
    }
  }

  private class FlushTask implements Runnable {
    @Override
    public void run() {
      // Don't call flush() because it would block the thread also used for sending the traces.
      disruptor.publishEvent(FLUSH_TRANSLATOR);
    }
  }

  // Exposing some statistics for consumption by monitors
  public final long getDisruptorCapacity() {
    return disruptor.getRingBuffer().getBufferSize();
  }

  public final long getDisruptorRemainingCapacity() {
    return disruptor.getRingBuffer().remainingCapacity();
  }
}
