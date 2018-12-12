package datadog.trace.tracer;

import datadog.trace.api.DDTags;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Concrete implementation of a span */
class SpanImpl implements Span {

  private final TraceInternal trace;

  private final SpanContext context;
  private final Timestamp startTimestamp;

  /* Note: some fields are volatile so we could make getters non synchronized.
  Alternatively we could make getters synchronized, but this may create more contention.
   */
  private volatile Long duration = null;

  private volatile String service;
  private volatile String resource;
  private volatile String type;
  private volatile String name;
  private volatile boolean errored = false;

  private final Map<String, Object> meta = new HashMap<>();

  private final List<Interceptor> interceptors;

  /**
   * Create a span with the a specific startTimestamp timestamp.
   *
   * @param trace The trace to associate this span with.
   * @param parentContext identifies the parent of this span. May be null.
   * @param startTimestamp timestamp when this span was started.
   */
  SpanImpl(
      final TraceInternal trace, final SpanContext parentContext, final Timestamp startTimestamp) {
    this.trace = trace;

    context = SpanContextImpl.fromParent(parentContext);

    if (startTimestamp == null) {
      reportUsageError("Cannot create span without timestamp: %s", trace);
      throw new TraceException(String.format("Cannot create span without timestamp: %s", trace));
    }
    this.startTimestamp = startTimestamp;
    service = trace.getTracer().getDefaultServiceName();
    interceptors = trace.getTracer().getInterceptors();

    for (final Interceptor interceptor : interceptors) {
      interceptor.afterSpanStarted(this);
    }
  }

  @Override
  public Trace getTrace() {
    return trace;
  }

  @Override
  public SpanContext getContext() {
    return context;
  }

  @Override
  public Timestamp getStartTimestamp() {
    return startTimestamp;
  }

  @Override
  public Long getDuration() {
    return duration;
  }

  @Override
  public boolean isFinished() {
    return duration != null;
  }

  @Override
  public String getService() {
    return service;
  }

  @Override
  public synchronized void setService(final String service) {
    if (isFinished()) {
      reportSetterUsageError("service");
    } else {
      this.service = service;
    }
  }

  @Override
  public String getResource() {
    return resource;
  }

  @Override
  public synchronized void setResource(final String resource) {
    if (isFinished()) {
      reportSetterUsageError("resource");
    } else {
      this.resource = resource;
    }
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public synchronized void setType(final String type) {
    if (isFinished()) {
      reportSetterUsageError("type");
    } else {
      this.type = type;
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public synchronized void setName(final String name) {
    if (isFinished()) {
      reportSetterUsageError("name");
    } else {
      this.name = name;
    }
  }

  @Override
  public boolean isErrored() {
    return errored;
  }

  @Override
  public synchronized void attachThrowable(final Throwable throwable) {
    if (isFinished()) {
      reportSetterUsageError("throwable");
    } else {
      setErrored(true);

      setMeta(DDTags.ERROR_MSG, throwable.getMessage());
      setMeta(DDTags.ERROR_TYPE, throwable.getClass().getName());

      final StringWriter errorString = new StringWriter();
      throwable.printStackTrace(new PrintWriter(errorString));
      setMeta(DDTags.ERROR_STACK, errorString.toString());
    }
  }

  @Override
  public synchronized void setErrored(final boolean errored) {
    if (isFinished()) {
      reportSetterUsageError("errored");
    } else {
      this.errored = errored;
    }
  }

  @Override
  public synchronized Object getMeta(final String key) {
    return meta.get(key);
  }

  protected synchronized void setMeta(final String key, final Object value) {
    if (isFinished()) {
      reportSetterUsageError("meta value " + key);
    } else {
      if (value == null) {
        meta.remove(key);
      } else {
        meta.put(key, value);
      }
    }
  }

  @Override
  public void setMeta(final String key, final String value) {
    setMeta(key, (Object) value);
  }

  @Override
  public void setMeta(final String key, final Boolean value) {
    setMeta(key, (Object) value);
  }

  @Override
  public void setMeta(final String key, final Number value) {
    setMeta(key, (Object) value);
  }

  @Override
  public synchronized void finish() {
    if (isFinished()) {
      reportUsageError("Attempted to finish span that is already finished: %s", this);
    } else {
      finishSpan(startTimestamp.getDuration(), false);
    }
  }

  @Override
  public synchronized void finish(final long finishTimestampNanoseconds) {
    if (isFinished()) {
      reportUsageError("Attempted to finish span that is already finish: %s", this);
    } else {
      finishSpan(startTimestamp.getDuration(finishTimestampNanoseconds), false);
    }
  }

  // TODO: we may want to reconsider usage of 'finalize'. One of the problems seems to be that
  // exceptions thrown in finalizer are eaten up and ignored, and may not even be logged by default.
  // This may lead to fun debugging sessions.
  @Override
  protected synchronized void finalize() {
    // Note: according to docs finalize is only called once for a given instance - even if instance
    // if 'revived' from the dead by passing reference to some other object and then dies again.
    if (!isFinished()) {
      trace
          .getTracer()
          .reportWarning(
              "Finishing span due to GC, this will prevent trace from being reported: %s", this);
      finishSpan(startTimestamp.getDuration(), true);
    }
  }

  /**
   * Helper method to perform operations to finish the span.
   *
   * <p>Note: This has to be called under object lock.
   *
   * @param duration duration of the span.
   * @param fromGC true iff we are closing span because it is being GCed, this will make trace
   *     invalid.
   */
  private void finishSpan(final long duration, final boolean fromGC) {
    // Run interceptors in 'reverse' order
    for (int i = interceptors.size() - 1; i >= 0; i--) {
      interceptors.get(i).beforeSpanFinished(this);
    }
    this.duration = duration;
    trace.finishSpan(this, fromGC);
  }

  private void reportUsageError(final String message, final Object... args) {
    trace.getTracer().reportError(message, args);
  }

  private void reportSetterUsageError(final String fieldName) {
    reportUsageError("Attempted to set '%s' when span is already finished: %s", fieldName, this);
  }
}
