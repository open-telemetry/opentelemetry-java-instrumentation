package datadog.trace;

import datadog.opentracing.DDTracer;
import datadog.trace.common.writer.ListWriter;
import io.opentracing.ActiveSpan;
import io.opentracing.Span;
import io.opentracing.Tracer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

public class DDTraceBenchmark {
  public static String SPAN_NAME = "span-benchmark";

  @State(Scope.Thread)
  public static class TraceState {
    public ListWriter traceCollector = new ListWriter();
    public Tracer tracer = new DDTracer(traceCollector);
    public ActiveSpan activeSpan = tracer.buildSpan(SPAN_NAME).startActive();
  }

  @Benchmark
  public Object testBuildSpan(final TraceState state) {
    return state.tracer.buildSpan(SPAN_NAME);
  }

  @Benchmark
  public Object testBuildStartSpan(final TraceState state) {
    return state.tracer.buildSpan(SPAN_NAME).startManual();
  }

  @Benchmark
  public Object testFullSpan(final TraceState state) {
    final Span span = state.tracer.buildSpan(SPAN_NAME).startManual();
    span.finish();
    return span;
  }

  @Benchmark
  public Object testBuildStartSpanActive(final TraceState state) {
    return state.tracer.buildSpan(SPAN_NAME).startActive();
  }

  @Benchmark
  public Object testFullActiveSpan(final TraceState state) {
    final ActiveSpan activeSpan = state.tracer.buildSpan(SPAN_NAME).startActive();
    activeSpan.deactivate();
    return activeSpan;
  }

  @Benchmark
  public Object testContinuationCapture(final TraceState state) {
    return state.activeSpan.capture();
  }

  @Benchmark
  public Object testContinuationActivate(final TraceState state) {
    return state.activeSpan.capture().activate();
  }
}
