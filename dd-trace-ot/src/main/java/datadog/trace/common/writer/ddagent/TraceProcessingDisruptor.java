package datadog.trace.common.writer.ddagent;

import com.lmax.disruptor.EventHandler;
import datadog.common.exec.DaemonThreadFactory;
import datadog.opentracing.DDSpan;
import datadog.opentracing.DDSpanContext;
import datadog.trace.common.writer.DDAgentWriter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Disruptor that takes completed traces and applies processing to them. Upon completion, the
 * serialized trace is published to {@link BatchWritingDisruptor}.
 *
 * <p>publishing to the buffer will not block the calling thread, but instead will return false if
 * the buffer is full. This is to avoid impacting an application thread.
 */
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
  protected DaemonThreadFactory getThreadFactory() {
    return DaemonThreadFactory.TRACE_PROCESSOR;
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
          if (1 < event.representativeCount && !event.data.isEmpty()) {
            // attempt to have agent scale the metrics properly
            ((DDSpan) event.data.get(0).getLocalRootSpan())
                .context()
                .setMetric(DDSpanContext.SAMPLE_RATE_KEY, 1d / event.representativeCount);
          }
          try {
            final byte[] serializedTrace = api.serializeTrace(event.data);
            batchWritingDisruptor.publish(serializedTrace, event.representativeCount);
            monitor.onSerialize(writer, event.data, serializedTrace);
            event.representativeCount = 0; // reset in case flush is invoked below.
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
