package datadog.trace.tracer;

import datadog.trace.api.Config;
import datadog.trace.tracer.sampling.Sampler;
import datadog.trace.tracer.writer.Writer;

/** A Tracer creates {@link Trace}s and holds common settings across traces. */
public class Tracer {
  /** Default service name if none provided on the trace or span */
  final String serviceName = null;
  /** Writer is an charge of reporting traces and spans to the desired endpoint */
  final Writer writer = null;
  /** Sampler defines the sampling policy in order to reduce the number of traces for instance */
  final Sampler sampler = null;
  /** Settings for this tracer. */
  final Config config = null;

  // TODO: doc inject and extract

  public <T> void inject(final SpanContext spanContext, final Object format, final T carrier) {}

  public <T> SpanContext extract(final Object format, final T carrier) {
    return null;
  }
}
