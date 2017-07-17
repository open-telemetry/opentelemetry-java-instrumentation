package com.datadoghq.trace.writer;

import com.datadoghq.trace.DDBaseSpan;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** List writer used by tests mostly */
public class ListWriter extends CopyOnWriteArrayList<List<DDBaseSpan<?>>> implements Writer {

  public List<List<DDBaseSpan<?>>> getList() {
    return this;
  }

  public List<DDBaseSpan<?>> firstTrace() {
    return get(0);
  }

  @Override
  public void write(final List<DDBaseSpan<?>> trace) {
    add(trace);
  }

  @Override
  public void start() {
    clear();
  }

  @Override
  public void close() {
    clear();
  }
}
