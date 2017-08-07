package com.datadoghq.trace.writer;

import com.datadoghq.trace.DDBaseSpan;
import com.datadoghq.trace.Service;
import java.util.List;
import java.util.Map;

/** A writer is responsible to send collected spans to some place */
public interface Writer {

  /**
   * Write a trace represented by the entire list of all the finished spans
   *
   * @param trace the list of spans to write
   */
  void write(List<DDBaseSpan<?>> trace);

  /**
   * Report additional service information to the endpoint
   *
   * @param services a list of extra information about services
   */
  void writeServices(Map<String, Service> services);

  /** Start the writer */
  void start();

  /**
   * Indicates to the writer that no future writing will come and it should terminates all
   * connections and tasks
   */
  void close();
}
