package datadog.trace.common.writer.ddagent;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import datadog.common.exec.DaemonThreadFactory;
import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class AbstractDisruptor<T> implements Closeable {

  protected final Disruptor<DisruptorEvent<T>> disruptor;

  public volatile boolean running = false;

  protected final DisruptorEvent.FlushTranslator<T> flushTranslator =
      new DisruptorEvent.FlushTranslator<>();
  protected final DisruptorEvent.DataTranslator<T> dataTranslator =
      new DisruptorEvent.DataTranslator<>();

  public AbstractDisruptor(final int disruptorSize, final EventHandler<DisruptorEvent<T>> handler) {
    disruptor =
        new Disruptor<>(
            new DisruptorEvent.Factory<T>(),
            Math.max(2, Integer.highestOneBit(disruptorSize - 1) << 1), // Next power of 2
            getThreadFactory(),
            ProducerType.MULTI,
            new SleepingWaitStrategy(0, TimeUnit.MILLISECONDS.toNanos(5)));
    disruptor.handleEventsWith(handler);
  }

  protected abstract DaemonThreadFactory getThreadFactory();

  public void start() {
    disruptor.start();
    running = true;
  }

  @Override
  public void close() {
    running = false;
    disruptor.shutdown();
  }

  /**
   * Allows the underlying publish to be defined as a blocking or non blocking call.
   *
   * @param data
   * @param representativeCount
   * @return
   */
  public abstract boolean publish(final T data, int representativeCount);

  /**
   * This method will block until the flush is complete.
   *
   * @param traceCount - number of unreported traces to include in this batch.
   */
  public boolean flush(final int traceCount) {
    if (running) {
      return flush(traceCount, new CountDownLatch(1));
    } else {
      return false;
    }
  }

  /** This method will block until the flush is complete. */
  protected boolean flush(final int traceCount, final CountDownLatch latch) {
    log.info("Flushing any remaining traces.");
    disruptor.publishEvent(flushTranslator, traceCount, latch);
    try {
      latch.await();
      return true;
    } catch (final InterruptedException e) {
      log.warn("Waiting for flush interrupted.", e);
      return false;
    }
  }

  // Exposing some statistics for consumption by monitors
  public final long getDisruptorCapacity() {
    return disruptor.getRingBuffer().getBufferSize();
  }

  public final long getDisruptorRemainingCapacity() {
    return disruptor.getRingBuffer().remainingCapacity();
  }

  public final long getCurrentCount() {
    return disruptor.getCursor() - disruptor.getRingBuffer().getMinimumGatingSequence();
  }
}
