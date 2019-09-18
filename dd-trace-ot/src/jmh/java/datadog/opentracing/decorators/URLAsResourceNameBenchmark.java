package datadog.opentracing.decorators;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import datadog.opentracing.DDSpanContext;
import datadog.opentracing.SpanFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.slf4j.LoggerFactory;

public class URLAsResourceNameBenchmark {
  static {
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
  }

  @State(Scope.Benchmark)
  public static class BenchmarkState {
    private final AbstractDecorator base = new URLAsResourceName();

    private final DDSpanContext ctx = SpanFactory.newSpanOf(0).context();
  }

  @Benchmark
  public Object testPathOnly(final BenchmarkState state) {
    return state.base.shouldSetTag(state.ctx, null, "/somepath/123/");
  }

  @Benchmark
  public Object testFullUrl(final BenchmarkState state) {
    return state.base.shouldSetTag(
        state.ctx, null, "http://localhost:8080/somepath/123/?query=123#fragment");
  }
}
