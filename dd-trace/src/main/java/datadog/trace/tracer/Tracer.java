package datadog.trace.tracer;

import datadog.trace.api.Config;
import datadog.trace.tracer.sampling.AllSampler;
import datadog.trace.tracer.sampling.Sampler;
import datadog.trace.tracer.writer.LoggingWriter;
import datadog.trace.tracer.writer.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** A Tracer creates {@link Trace}s and holds common settings across traces. */
@Slf4j
public class Tracer {

  /** Writer is an charge of reporting traces and spans to the desired endpoint */
  private final Writer writer;
  /** Sampler defines the sampling policy in order to reduce the number of traces for instance */
  private final Sampler sampler;
  /** Settings for this tracer. */
  private final Config config;

  /** Interceptors to be called on certain trace and span events */
  private final List<Interceptor> interceptors;

  public Tracer() {
    this(Config.get());
  }

  public Tracer(final List<Interceptor> interceptors) {
    this(Config.get(), interceptors);
  }

  public Tracer(final Config config) {
    this(config, Collections.<Interceptor>emptyList());
  }

  public Tracer(final Config config, final List<Interceptor> interceptors) {
    // TODO: implement and include "standard" interceptors
    this(
        config,
        new LoggingWriter(),
        new AllSampler(),
        Collections.unmodifiableList(new ArrayList<>(interceptors)));
  }

  Tracer(
      final Config config,
      final Writer writer,
      final Sampler sampler,
      final List<Interceptor> interceptors) {
    this.config = config;
    // TODO: we probably need to implement some sort of 'close' method for Tracer
    this.writer = writer;
    this.sampler = sampler;
    this.interceptors = Collections.unmodifiableList(new ArrayList<>(interceptors));
  }

  /** @return {@link Writer} used by this tracer */
  public Writer getWriter() {
    return writer;
  }

  /** @return {@link Sampler} used by this tracer. */
  public Sampler getSampler() {
    return sampler;
  }

  /** @return unmodifiable list of trace/span interceptors. */
  public List<Interceptor> getInterceptors() {
    return interceptors;
  }

  /** @return service name to use on span by default. */
  String getDefaultServiceName() {
    return config.getServiceName();
  }

  /**
   * @return timestamp for current time. Note: this is mainly useful when there is no 'current'
   *     trace. If there is 'current' trace already then one should use it to get timestamps.
   */
  public Timestamp createCurrentTimestamp() {
    return new Clock(this).createCurrentTimestamp();
  }

  /**
   * Construct a new trace using this tracer's settings and return the root span.
   *
   * @param parentContext parent context of a root span in this trace. May be null.
   * @return The root span of the new trace.
   */
  public Span buildTrace(final SpanContext parentContext) {
    return buildTrace(parentContext, createCurrentTimestamp());
  }

  /**
   * Construct a new trace using this tracer's settings and return the root span.
   *
   * @param parentContext parent context of a root span in this trace. May be null.
   * @param timestamp root span start timestamp.
   * @return The root span of the new trace.
   */
  public Span buildTrace(final SpanContext parentContext, final Timestamp timestamp) {
    final Trace trace = new TraceImpl(this, parentContext, timestamp);
    return trace.getRootSpan();
  }

  // TODO: doc inject and extract
  // TODO: inject and extract helpers on span context?
  public <T> void inject(final SpanContext spanContext, final Object format, final T carrier) {}

  public <T> SpanContext extract(final Object format, final T carrier) {
    return null;
  }

  // TODO: we can move these two into a separate class and move all '*Impl' and '*Internal' classes
  // into separate package
  // TODO: provide some sort of ratelimiting here to avoid filling up disk space.
  /*
   Report warning to the log/console. This never throws exception
  */
  void reportWarning(final String message, final Object... args) {
    final String completeMessage = String.format(message, args);
    log.debug(completeMessage);
  }

  /*
   Report error to the log/console. This may throw an exception
  */
  void reportError(final String message, final Object... args) {
    // TODO: Provide way to do logging or throwing an exception according to config?
    final String completeMessage = String.format(message, args);
    log.debug(completeMessage);
    throw new TraceException(completeMessage);
  }
}
