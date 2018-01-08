package com.datadoghq.trace.writer;

import com.datadoghq.trace.DDBaseSpan;
import com.datadoghq.trace.Service;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/** List writer used by tests mostly */
public class ListWriter extends CopyOnWriteArrayList<List<DDBaseSpan<?>>> implements Writer {
  private final List<CountDownLatch> latches = new LinkedList<>();

  public List<DDBaseSpan<?>> firstTrace() {
    return get(0);
  }

  @Override
  public void write(final List<DDBaseSpan<?>> trace) {
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

  public void waitForTraces(final int number) throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(number);
    synchronized (latches) {
      if (size() >= number) {
        return;
      }
      latches.add(latch);
    }
    latch.await();
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
