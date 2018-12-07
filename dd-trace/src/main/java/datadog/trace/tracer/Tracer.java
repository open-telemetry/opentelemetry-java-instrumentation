package datadog.trace.tracer;

import datadog.trace.api.Config;
import datadog.trace.tracer.sampling.Sampler;
import datadog.trace.tracer.writer.Writer;

/** A Tracer creates {@link Trace}s and holds common settings across traces. */
public class Tracer {
  /** Default service name if none provided on the trace or span */
  private final String defaultServiceName = null;
  /** Writer is an charge of reporting traces and spans to the desired endpoint */
  private final Writer writer = null;
  /** Sampler defines the sampling policy in order to reduce the number of traces for instance */
  private final Sampler sampler = null;
  /** Settings for this tracer. */
  private final Config config = null;
  /** The clock to use for tracing. */
  private final Clock clock = null;

  /**
   * Construct a new trace using this tracer's settings and return the root span.
   *
   * @return The root span of the new trace.
   */
  public Span buildTrace(final SpanContext parentContext) {
    return null;
  }

  /**
   * Construct a new trace using this tracer's settings and return the root span.
   *
   * @param rootSpanStartTimestampNanoseconds Epoch time in nanoseconds when the root span started.
   * @return The root span of the new trace.
   */
  public Span buildTrace(
      final SpanContext parentContext, final long rootSpanStartTimestampNanoseconds) {
    return null;
  }

  // TODO: doc inject and extract
  // TODO: inject and extract helpers on span context?
  public <T> void inject(final SpanContext spanContext, final Object format, final T carrier) {}

  public <T> SpanContext extract(final Object format, final T carrier) {
    return null;
  }
}
