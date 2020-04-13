package datadog.trace.common.writer.ddagent;

import com.lmax.disruptor.EventHandler;
import datadog.common.exec.CommonTaskExecutor;
import datadog.common.exec.CommonTaskExecutor.Task;
import datadog.common.exec.DaemonThreadFactory;
import datadog.trace.common.writer.DDAgentWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Disruptor that takes serialized traces and batches them into appropriately sized requests.
 *
 * <p>publishing to the buffer will block if the buffer is full.
 */
@Slf4j
public class BatchWritingDisruptor extends AbstractDisruptor<byte[]> {
  private static final int FLUSH_PAYLOAD_BYTES = 5_000_000; // 5 MB

  private final DisruptorEvent.HeartbeatTranslator<byte[]> heartbeatTranslator =
      new DisruptorEvent.HeartbeatTranslator();

  public BatchWritingDisruptor(
      final int disruptorSize,
      final int flushFrequencySeconds,
      final DDAgentApi api,
      final Monitor monitor,
      final DDAgentWriter writer) {
    super(disruptorSize, new BatchWritingHandler(flushFrequencySeconds, api, monitor, writer));

    if (0 < flushFrequencySeconds) {
      // This provides a steady stream of events to enable flushing with a low throughput.
      CommonTaskExecutor.INSTANCE.scheduleAtFixedRate(
          new HeartbeatTask(), this, 100, 100, TimeUnit.MILLISECONDS, "disruptor heartbeat");
    }
  }

  @Override
  protected DaemonThreadFactory getThreadFactory() {
    return DaemonThreadFactory.TRACE_WRITER;
  }

  @Override
  public boolean publish(final byte[] data, final int representativeCount) {
    // blocking call to ensure serialized traces aren't discarded and apply back pressure.
    disruptor.getRingBuffer().publishEvent(dataTranslator, data, representativeCount);
    return true;
  }

  private void heartbeat() {
    if (running && getCurrentCount() == 0) {
      disruptor.getRingBuffer().tryPublishEvent(heartbeatTranslator);
    }
  }

  // Intentionally not thread safe.
  private static class BatchWritingHandler implements EventHandler<DisruptorEvent<byte[]>> {

    private final long flushFrequencyNanos;
    private final DDAgentApi api;
    private final Monitor monitor;
    private final DDAgentWriter writer;
    private final List<byte[]> serializedTraces = new ArrayList<>();
    private int representativeCount = 0;
    private int sizeInBytes = 0;
    private long nextScheduledFlush;

    private BatchWritingHandler(
        final int flushFrequencySeconds,
        final DDAgentApi api,
        final Monitor monitor,
        final DDAgentWriter writer) {
      flushFrequencyNanos = TimeUnit.SECONDS.toNanos(flushFrequencySeconds);
      scheduleNextFlush();
      this.api = api;
      this.monitor = monitor;
      this.writer = writer;
    }

    // TODO: reduce byte[] garbage by keeping the byte[] on the event and copy before returning.
    @Override
    public void onEvent(
        final DisruptorEvent<byte[]> event, final long sequence, final boolean endOfBatch) {
      try {
        if (event.data != null) {
          sizeInBytes += event.data.length;
          serializedTraces.add(event.data);
        }

        // Flush events might increase this with no data.
        representativeCount += event.representativeCount;

        if (event.flushLatch != null
            || FLUSH_PAYLOAD_BYTES <= sizeInBytes
            || nextScheduledFlush <= System.nanoTime()) {
          flush(event.flushLatch, FLUSH_PAYLOAD_BYTES <= sizeInBytes);
        }
      } finally {
        event.reset();
      }
    }

    private void flush(final CountDownLatch flushLatch, final boolean early) {
      try {
        if (serializedTraces.isEmpty()) {
          // FIXME: this will reset representativeCount without reporting
          //  anything even if representativeCount > 0.
          return;
        }

        // TODO add retry and rate limiting
        final DDAgentApi.Response response =
            api.sendSerializedTraces(representativeCount, sizeInBytes, serializedTraces);

        monitor.onFlush(writer, early);

        if (response.success()) {
          log.debug("Successfully sent {} traces to the API", serializedTraces.size());

          monitor.onSend(writer, representativeCount, sizeInBytes, response);
        } else {
          log.debug(
              "Failed to send {} traces (representing {}) of size {} bytes to the API",
              serializedTraces.size(),
              representativeCount,
              sizeInBytes);

          monitor.onFailedSend(writer, representativeCount, sizeInBytes, response);
        }
      } catch (final Throwable e) {
        log.debug("Failed to send traces to the API: {}", e.getMessage());

        // DQH - 10/2019 - DDApi should wrap most exceptions itself, so this really
        // shouldn't occur.
        // However, just to be safe to start, create a failed Response to handle any
        // spurious Throwable-s.
        monitor.onFailedSend(
            writer, representativeCount, sizeInBytes, DDAgentApi.Response.failed(e));
      } finally {
        serializedTraces.clear();
        sizeInBytes = 0;
        representativeCount = 0;
        scheduleNextFlush();

        if (flushLatch != null) {
          flushLatch.countDown();
        }
      }
    }

    private void scheduleNextFlush() {
      // TODO: adjust this depending on responsiveness of the agent.
      if (0 < flushFrequencyNanos) {
        nextScheduledFlush = System.nanoTime() + flushFrequencyNanos;
      } else {
        nextScheduledFlush = Long.MAX_VALUE;
      }
    }
  }

  // Important to use explicit class to avoid implicit hard references to BatchWritingDisruptor
  private static final class HeartbeatTask implements Task<BatchWritingDisruptor> {
    @Override
    public void run(final BatchWritingDisruptor target) {
      target.heartbeat();
    }
  }
}
