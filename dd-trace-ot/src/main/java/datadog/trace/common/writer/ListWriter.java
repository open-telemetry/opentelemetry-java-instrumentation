package datadog.trace.common.writer;

import datadog.opentracing.DDSpan;
import datadog.trace.common.Service;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** List writer used by tests mostly */
public class ListWriter extends CopyOnWriteArrayList<List<DDSpan>> implements Writer {
  private final List<CountDownLatch> latches = new LinkedList<>();

  public List<DDSpan> firstTrace() {
    return get(0);
  }

  @Override
  public void write(final List<DDSpan> trace) {
    synchronized (latches) {
      add(trace);
      for (final CountDownLatch latch : latches) {
        if (size() >= latch.getCount()) {
          while (latch.getCount() > 0) {
            latch.countDown();
          }
        }
      }
    }
  }

  public void waitForTraces(final int number) throws InterruptedException, TimeoutException {
    final CountDownLatch latch = new CountDownLatch(number);
    synchronized (latches) {
      if (size() >= number) {
        return;
      }
      latches.add(latch);
    }
    if (!latch.await(5, TimeUnit.SECONDS)) {
      throw new TimeoutException("Timeout waiting for " + number + " trace(s).");
    }
  }

  @Override
  public void writeServices(final Map<String, Service> services) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void start() {
    close();
  }

  @Override
  public void close() {
    clear();
    synchronized (latches) {
      for (final CountDownLatch latch : latches) {
        while (latch.getCount() > 0) {
          latch.countDown();
        }
      }
      latches.clear();
    }
  }

  @Override
  public String toString() {
    return "ListWriter { size=" + this.size() + " }";
  }
}
