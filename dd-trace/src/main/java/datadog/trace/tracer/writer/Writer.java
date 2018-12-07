package datadog.trace.tracer.writer;

import datadog.trace.tracer.Trace;

/** A writer sends traces to some place. */
public interface Writer {
  /**
   * Write a trace represented by the entire list of all the finished spans
   *
   * @param trace the trace to write
   */
  void write(Trace trace);

  /**
   * Inform the writer that a trace occurred but will not be written. Used by tracer-side sampling.
   */
  void incrementTraceCount();

  /** Start the writer */
  void start();

  /**
   * Indicates to the writer that no future writing will come and it should terminates all
   * connections and tasks
   */
  void close();
}
