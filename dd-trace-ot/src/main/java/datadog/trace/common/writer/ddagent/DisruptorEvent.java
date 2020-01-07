package datadog.trace.common.writer.ddagent;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.EventTranslatorTwoArg;
import java.util.concurrent.CountDownLatch;

class DisruptorEvent<T> {
  // Memory ordering enforced by disruptor's memory fences, so volatile not required.
  T data = null;
  int representativeCount = 0;
  CountDownLatch flushLatch = null;

  void reset() {
    data = null;
    representativeCount = 0;
    flushLatch = null;
  }

  static class Factory<T> implements EventFactory<DisruptorEvent<T>> {
    @Override
    public DisruptorEvent<T> newInstance() {
      return new DisruptorEvent<>();
    }
  }

  static class DataTranslator<T> implements EventTranslatorTwoArg<DisruptorEvent<T>, T, Integer> {

    @Override
    public void translateTo(
        final DisruptorEvent<T> event,
        final long sequence,
        final T data,
        final Integer representativeCount) {
      event.data = data;
      event.representativeCount = representativeCount;
    }
  }

  static class HeartbeatTranslator<T> implements EventTranslator<DisruptorEvent<T>> {

    @Override
    public void translateTo(final DisruptorEvent<T> event, final long sequence) {
      return;
    }
  }

  static class FlushTranslator<T>
      implements EventTranslatorTwoArg<DisruptorEvent<T>, Integer, CountDownLatch> {

    @Override
    public void translateTo(
        final DisruptorEvent<T> event,
        final long sequence,
        final Integer representativeCount,
        final CountDownLatch latch) {
      event.representativeCount = representativeCount;
      event.flushLatch = latch;
    }
  }
}
