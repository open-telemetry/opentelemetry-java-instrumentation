package datadog.trace.common.writer.ddagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.lmax.disruptor.EventHandler;
import datadog.opentracing.DDSpan;
import datadog.trace.common.util.DaemonThreadFactory;
import datadog.trace.common.writer.DDAgentWriter;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TraceProcessingDisruptor extends AbstractDisruptor<List<DDSpan>> {

  public TraceProcessingDisruptor(
      final int disruptorSize,
      final DDAgentApi api,
      final BatchWritingDisruptor batchWritingDisruptor,
      final Monitor monitor,
      final DDAgentWriter writer) {
    // TODO: add config to enable control over serialization overhead.
    super(disruptorSize, new TraceSerializingHandler(api, batchWritingDisruptor, monitor, writer));
  }

  @Override
  protected ThreadFactory getThreadFactory() {
    return new DaemonThreadFactory("dd-trace-processor");
  }

  @Override
  public boolean publish(final List<DDSpan> data, final int representativeCount) {
    return disruptor.getRingBuffer().tryPublishEvent(dataTranslator, data, representativeCount);
  }

  // This class is threadsafe if we want to enable more processors.
  public static class TraceSerializingHandler
      implements EventHandler<DisruptorEvent<List<DDSpan>>> {
    private final DDAgentApi api;
    private final BatchWritingDisruptor batchWritingDisruptor;
    private final Monitor monitor;
    private final DDAgentWriter writer;

    public TraceSerializingHandler(
        final DDAgentApi api,
        final BatchWritingDisruptor batchWritingDisruptor,
        final Monitor monitor,
        final DDAgentWriter writer) {
      this.api = api;
      this.batchWritingDisruptor = batchWritingDisruptor;
      this.monitor = monitor;
      this.writer = writer;
    }

    @Override
    public void onEvent(
        final DisruptorEvent<List<DDSpan>> event, final long sequence, final boolean endOfBatch) {
      try {
        if (event.data != null) {
          try {
            final byte[] serializedTrace = api.serializeTrace(event.data);
            monitor.onSerialize(writer, event.data, serializedTrace);
            batchWritingDisruptor.publish(serializedTrace, event.representativeCount);
            event.representativeCount = 0; // reset in case flush is invoked below.
          } catch (final JsonProcessingException e) {
            log.debug("Error serializing trace", e);
            monitor.onFailedSerialize(writer, event.data, e);
          } catch (final Throwable e) {
            log.debug("Error while serializing trace", e);
            monitor.onFailedSerialize(writer, event.data, e);
          }
        }

        if (event.flushLatch != null) {
          if (batchWritingDisruptor.running) {
            // propagate the flush.
            batchWritingDisruptor.flush(event.representativeCount, event.flushLatch);
          }
          if (!batchWritingDisruptor.running) { // check again to protect against race condition.
            // got shutdown early somehow?
            event.flushLatch.countDown();
          }
        }
      } finally {
        event.reset();
      }
    }
  }
}
