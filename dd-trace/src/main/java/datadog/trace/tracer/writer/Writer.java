package datadog.trace.tracer.writer;

import datadog.trace.tracer.Trace;
import java.util.List;

/** A writer sends traces to some place. */
public interface Writer {
  /**
   * Write a trace represented by the entire list of all the finished spans
   *
   * @param traces the list of traces to write
   */
  void write(List<Trace> traces);

  /**
   * Inform the writer that traces occurred but will not be written. Used by tracer-side sampling.
   *
   * @param numTraces the number of traces to increment the writer's total count by.
   */
  void incrementTraceCount(int numTraces);

  /** Start the writer */
  void start();

  /**
   * Indicates to the writer that no future writing will come and it should terminates all
   * connections and tasks
   */
  void close();
}
