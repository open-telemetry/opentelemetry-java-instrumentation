package datadog.trace.tracer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import datadog.trace.api.DDTags;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/** Concrete implementation of a span */
@Slf4j
// Disable autodetection of fields and accessors
@JsonAutoDetect(
    fieldVisibility = Visibility.NONE,
    setterVisibility = Visibility.NONE,
    getterVisibility = Visibility.NONE,
    isGetterVisibility = Visibility.NONE,
    creatorVisibility = Visibility.NONE)
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

  @JsonGetter("trace_id")
  @JsonSerialize(using = UInt64IDStringSerializer.class)
  public String getTraceId() {
    return context.getTraceId();
  }

  @JsonGetter("span_id")
  @JsonSerialize(using = UInt64IDStringSerializer.class)
  public String getSpanId() {
    return context.getSpanId();
  }

  @JsonGetter("parent_id")
  @JsonSerialize(using = UInt64IDStringSerializer.class)
  public String getParentId() {
    return context.getParentId();
  }

  @Override
  @JsonGetter("start")
  public Timestamp getStartTimestamp() {
    return startTimestamp;
  }

  @Override
  @JsonGetter("duration")
  public Long getDuration() {
    return duration;
  }

  @Override
  public boolean isFinished() {
    return duration != null;
  }

  @Override
  @JsonGetter("service")
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
  @JsonGetter("resource")
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
  @JsonGetter("type")
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
  @JsonGetter("name")
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
  @JsonGetter("error")
  @JsonFormat(shape = JsonFormat.Shape.NUMBER)
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
  public synchronized Map<String, Object> getMeta() {
    return Collections.unmodifiableMap(meta);
  }

  /**
   * The agent expects meta's values to be strings.
   *
   * @return a copy of meta with all values converted to strings.
   */
  @JsonGetter("meta")
  synchronized Map<String, String> getMetaString() {
    final Map<String, String> result = new HashMap<>(meta.size());
    for (final Map.Entry<String, Object> entry : meta.entrySet()) {
      result.put(entry.getKey(), String.valueOf(entry.getValue()));
    }
    return result;
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

  // FIXME: Add metrics support and json rendering for metrics

  @Override
  public synchronized void finish() {
    if (isFinished()) {
      reportUsageError("Attempted to finish span that is already finished: %s", this);
    } else {
      finishSpan(startTimestamp.getDuration(), false);
    }
  }

  // FIXME: This should take a Timestamp object instead.
  @Override
  public synchronized void finish(final long finishTimestampNanoseconds) {
    if (isFinished()) {
      reportUsageError("Attempted to finish span that is already finish: %s", this);
    } else {
      finishSpan(startTimestamp.getDuration(finishTimestampNanoseconds), false);
    }
  }

  @Override
  protected synchronized void finalize() {
    try {
      // Note: according to docs finalize is only called once for a given instance - even if
      // instance is 'revived' from the dead by passing reference to some other object and
      // then dies again.
      if (!isFinished()) {
        log.debug(
            "Finishing span due to GC, this will prevent trace from being reported: {}", this);
        finishSpan(startTimestamp.getDuration(), true);
      }
    } catch (final Throwable t) {
      // Exceptions thrown in finalizer are eaten up and ignored, so log them instead
      log.debug("Span finalizer had thrown an exception: ", t);
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

  /** Helper to serialize string value as 64 bit unsigned integer */
  private static class UInt64IDStringSerializer extends StdSerializer<String> {

    public UInt64IDStringSerializer() {
      super(String.class);
    }

    @Override
    public void serialize(
        final String value, final JsonGenerator jsonGenerator, final SerializerProvider provider)
        throws IOException {
      jsonGenerator.writeNumber(new BigInteger(value));
    }
  }
}
