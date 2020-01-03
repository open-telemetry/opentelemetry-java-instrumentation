package datadog.trace.common.writer.ddagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.lmax.disruptor.EventHandler;
import datadog.opentracing.DDSpan;
import datadog.trace.common.writer.DDAgentWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/** This class is intentionally not threadsafe. */
@Slf4j
public class TraceConsumer implements EventHandler<DisruptorEvent<List<DDSpan>>> {
  private static final int FLUSH_PAYLOAD_BYTES = 5_000_000; // 5 MB

  private final AtomicInteger traceCount;
  private final Semaphore senderSemaphore;
  private final DDAgentWriter writer;

  private List<byte[]> serializedTraces = new ArrayList<>();
  private int payloadSize = 0;

  public TraceConsumer(
      final AtomicInteger traceCount, final int senderQueueSize, final DDAgentWriter writer) {
    this.traceCount = traceCount;
    senderSemaphore = new Semaphore(senderQueueSize);
    this.writer = writer;
  }

  @Override
  public void onEvent(
      final DisruptorEvent<List<DDSpan>> event, final long sequence, final boolean endOfBatch) {
    final List<DDSpan> trace = event.data;
    event.data = null; // clear the event for reuse.
    if (trace != null) {
      traceCount.incrementAndGet();
      try {
        final byte[] serializedTrace = writer.getApi().serializeTrace(trace);
        payloadSize += serializedTrace.length;
        serializedTraces.add(serializedTrace);

        writer.monitor.onSerialize(writer, trace, serializedTrace);
      } catch (final JsonProcessingException e) {
        log.warn("Error serializing trace", e);

        writer.monitor.onFailedSerialize(writer, trace, e);
      } catch (final Throwable e) {
        log.debug("Error while serializing trace", e);

        writer.monitor.onFailedSerialize(writer, trace, e);
      }
    }

    if (event.shouldFlush || payloadSize >= FLUSH_PAYLOAD_BYTES) {
      final boolean early = (payloadSize >= FLUSH_PAYLOAD_BYTES);

      reportTraces(early);
      event.shouldFlush = false;
    }
  }

  private void reportTraces(final boolean early) {
    try {
      if (serializedTraces.isEmpty()) {
        writer.monitor.onFlush(writer, early);

        writer.apiPhaser.arrive(); // Allow flush to return
        return;
        // scheduleFlush called in finally block.
      }
      if (writer.scheduledWriterExecutor.isShutdown()) {
        writer.monitor.onFailedSend(
            writer, traceCount.get(), payloadSize, DDAgentApi.Response.failed(-1));
        writer.apiPhaser.arrive(); // Allow flush to return
        return;
      }
      final List<byte[]> toSend = serializedTraces;
      serializedTraces = new ArrayList<>(toSend.size());
      // ^ Initialize with similar size to reduce arraycopy churn.

      final int representativeCount = traceCount.getAndSet(0);
      final int sizeInBytes = payloadSize;

      try {
        writer.monitor.onFlush(writer, early);

        // Run the actual IO task on a different thread to avoid blocking the consumer.
        try {
          senderSemaphore.acquire();
        } catch (final InterruptedException e) {
          writer.monitor.onFailedSend(
              writer, representativeCount, sizeInBytes, DDAgentApi.Response.failed(e));

          // Finally, we'll schedule another flush
          // Any threads awaiting the flush will continue to wait
          return;
        }
        writer.scheduledWriterExecutor.execute(
            new Runnable() {
              @Override
              public void run() {
                senderSemaphore.release();

                try {
                  final DDAgentApi.Response response =
                      writer
                          .getApi()
                          .sendSerializedTraces(representativeCount, sizeInBytes, toSend);

                  if (response.success()) {
                    log.debug("Successfully sent {} traces to the API", toSend.size());

                    writer.monitor.onSend(writer, representativeCount, sizeInBytes, response);
                  } else {
                    log.debug(
                        "Failed to send {} traces (representing {}) of size {} bytes to the API",
                        toSend.size(),
                        representativeCount,
                        sizeInBytes);

                    writer.monitor.onFailedSend(writer, representativeCount, sizeInBytes, response);
                  }
                } catch (final Throwable e) {
                  log.debug("Failed to send traces to the API: {}", e.getMessage());

                  // DQH - 10/2019 - DDApi should wrap most exceptions itself, so this really
                  // shouldn't occur.
                  // However, just to be safe to start, create a failed Response to handle any
                  // spurious Throwable-s.
                  writer.monitor.onFailedSend(
                      writer, representativeCount, sizeInBytes, DDAgentApi.Response.failed(e));
                } finally {
                  writer.apiPhaser.arrive(); // Flush completed.
                }
              }
            });
      } catch (final RejectedExecutionException ex) {
        writer.monitor.onFailedSend(
            writer, representativeCount, sizeInBytes, DDAgentApi.Response.failed(ex));
        writer.apiPhaser.arrive(); // Allow flush to return
      }
    } finally {
      payloadSize = 0;
      writer.disruptor.scheduleFlush();
    }
  }
}
