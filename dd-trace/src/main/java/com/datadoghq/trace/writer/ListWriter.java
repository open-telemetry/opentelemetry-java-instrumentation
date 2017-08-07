package com.datadoghq.trace.writer;

import com.datadoghq.trace.DDBaseSpan;
import com.datadoghq.trace.Service;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

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
  public void writeServices(final List<Service> services) {
    throw new NotImplementedException();
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
