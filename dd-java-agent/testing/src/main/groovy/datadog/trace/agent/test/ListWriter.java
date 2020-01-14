package datadog.trace.agent.test;

import com.google.common.base.Stopwatch;
import io.opentelemetry.sdk.trace.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ListWriter extends CopyOnWriteArrayList<List<SpanData>> implements SpanExporter {

  private final Object lock = new Object();

  @Override
  public ResultCode export(final List<SpanData> spans) {
    synchronized (lock) {
      for (final SpanData span : spans) {
        boolean found = false;
        for (final List<SpanData> trace : this) {
          if (trace.get(0).getTraceId().equals(span.getTraceId())) {
            trace.add(0, span);
            found = true;
            break;
          }
        }
        if (!found) {
          final List<SpanData> trace = new CopyOnWriteArrayList<>();
          trace.add(span);
          add(trace);
        }
      }
      lock.notifyAll();
    }
    return ResultCode.SUCCESS;
  }

  public void waitForTraces(final int number) throws InterruptedException, TimeoutException {
    synchronized (lock) {
      long remainingWaitMillis = TimeUnit.SECONDS.toMillis(20);
      while (completedTraceCount() < number && remainingWaitMillis > 0) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        lock.wait(remainingWaitMillis);
        remainingWaitMillis -= stopwatch.elapsed(TimeUnit.MILLISECONDS);
      }
      final int completedTraceCount = completedTraceCount();
      if (completedTraceCount < number) {
        throw new TimeoutException(
            "Timeout waiting for "
                + number
                + " completed trace(s), found "
                + completedTraceCount
                + " completed trace(s) and "
                + size()
                + " total trace(s)");
      }
    }
  }

  @Override
  public void shutdown() {}

  private int completedTraceCount() {
    int count = 0;
    for (final List<SpanData> trace : this) {
      if (isCompleted(trace)) {
        count++;
      }
    }
    return count;
  }

  // trace is completed if root span is present
  private boolean isCompleted(final List<SpanData> trace) {
    for (final SpanData span : trace) {
      if (!span.getParentSpanId().isValid()) {
        return true;
      }
      if (span.getParentSpanId().toLowerBase16().equals("0000000000000456")) {
        // this is a special parent id that some tests use
        return true;
      }
    }
    return false;
  }
}
