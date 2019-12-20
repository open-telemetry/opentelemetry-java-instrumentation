package datadog.trace.common.writer.ddagent;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.EventTranslatorOneArg;
import datadog.opentracing.DDSpan;
import java.util.List;

class DisruptorEvent<T> {
  public volatile boolean shouldFlush = false;
  public volatile T data = null;

  static class Factory<T> implements EventFactory<DisruptorEvent<T>> {
    @Override
    public DisruptorEvent<T> newInstance() {
      return new DisruptorEvent<>();
    }
  }

  static class TraceTranslator
      implements EventTranslatorOneArg<DisruptorEvent<List<DDSpan>>, List<DDSpan>> {
    static final DisruptorEvent.TraceTranslator TRACE_TRANSLATOR =
        new DisruptorEvent.TraceTranslator();

    @Override
    public void translateTo(
        final DisruptorEvent<List<DDSpan>> event, final long sequence, final List<DDSpan> trace) {
      event.data = trace;
    }
  }

  static class FlushTranslator implements EventTranslator<DisruptorEvent<List<DDSpan>>> {
    static final DisruptorEvent.FlushTranslator FLUSH_TRANSLATOR =
        new DisruptorEvent.FlushTranslator();

    @Override
    public void translateTo(final DisruptorEvent<List<DDSpan>> event, final long sequence) {
      event.shouldFlush = true;
    }
  }
}
