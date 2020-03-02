package datadog.opentracing;

import datadog.trace.api.DDTags;
import datadog.trace.api.interceptor.MutableSpan;
import datadog.trace.api.sampling.PrioritySampling;
import datadog.trace.common.util.Clock;
import io.opentracing.Span;
import io.opentracing.tag.Tag;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a period of time. Associated information is stored in the SpanContext.
 *
 * <p>Spans are created by the {@link DDTracer#buildSpan}. This implementation adds some features
 * according to the DD agent.
 */
@Slf4j
public class DDSpan implements Span, MutableSpan {

  /** The context attached to the span */
  private final DDSpanContext context;

  /**
   * Creation time of the span in microseconds provided by external clock. Must be greater than
   * zero.
   */
  private final long startTimeMicro;

  /**
   * Creation time of span in nanoseconds. We use combination of millisecond-precision clock and
   * nanosecond-precision offset from start of the trace. See {@link PendingTrace} for details. Must
   * be greater than zero.
   */
  private final long startTimeNano;

  /**
   * The duration in nanoseconds computed using the startTimeMicro or startTimeNano. Span is
   * considered finished when this is set.
   */
  private final AtomicLong durationNano = new AtomicLong();

  /** Delegates to for handling the logs if present. */
  private final LogHandler logHandler;

  /** Implementation detail. Stores the weak reference to this span. Used by TraceCollection. */
  volatile WeakReference<DDSpan> ref;

  /**
   * Spans should be constructed using the builder, not by calling the constructor directly.
   *
   * @param timestampMicro if greater than zero, use this time instead of the current time
   * @param context the context used for the span
   */
  DDSpan(final long timestampMicro, final DDSpanContext context) {
    this(timestampMicro, context, new DefaultLogHandler());
  }

  /**
   * Spans should be constructed using the builder, not by calling the constructor directly.
   *
   * @param timestampMicro if greater than zero, use this time instead of the current time
   * @param context the context used for the span
   * @param logHandler as the handler where to delegate the log actions
   */
  DDSpan(final long timestampMicro, final DDSpanContext context, final LogHandler logHandler) {
    this.context = context;
    this.logHandler = logHandler;

    if (timestampMicro <= 0L) {
      // record the start time
      startTimeMicro = Clock.currentMicroTime();
      startTimeNano = context.getTrace().getCurrentTimeNano();
    } else {
      startTimeMicro = timestampMicro;
      // Timestamp have come from an external clock, so use startTimeNano as a flag
      startTimeNano = 0;
    }

    context.getTrace().registerSpan(this);
  }

  public boolean isFinished() {
    return durationNano.get() != 0;
  }

  private void finishAndAddToTrace(final long durationNano) {
    // ensure a min duration of 1
    if (this.durationNano.compareAndSet(0, Math.max(1, durationNano))) {
      log.debug("Finished: {}", this);
      context.getTrace().addSpan(this);
    } else {
      log.debug("{} - already finished!", this);
    }
  }

  @Override
  public final void finish() {
    if (startTimeNano > 0) {
      // no external clock was used, so we can rely on nano time
      finishAndAddToTrace(context.getTrace().getCurrentTimeNano() - startTimeNano);
    } else {
      finish(Clock.currentMicroTime());
    }
  }

  @Override
  public final void finish(final long stoptimeMicros) {
    finishAndAddToTrace(TimeUnit.MICROSECONDS.toNanos(stoptimeMicros - startTimeMicro));
  }

  @Override
  public DDSpan setError(final boolean error) {
    context.setErrorFlag(error);
    return this;
  }

  /**
   * Check if the span is the root parent. It means that the traceId is the same as the spanId. In
   * the context of distributed tracing this will return true if an only if this is the application
   * initializing the trace.
   *
   * @return true if root, false otherwise
   */
  public final boolean isRootSpan() {
    return BigInteger.ZERO.equals(context.getParentId());
  }

  @Override
  @Deprecated
  public MutableSpan getRootSpan() {
    return getLocalRootSpan();
  }

  @Override
  public MutableSpan getLocalRootSpan() {
    return context().getTrace().getRootSpan();
  }

  public void setErrorMeta(final Throwable error) {
    setError(true);

    setTag(DDTags.ERROR_MSG, error.getMessage());
    setTag(DDTags.ERROR_TYPE, error.getClass().getName());

    final StringWriter errorString = new StringWriter();
    error.printStackTrace(new PrintWriter(errorString));
    setTag(DDTags.ERROR_STACK, errorString.toString());
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#setTag(java.lang.String, java.lang.String)
   */
  @Override
  public final DDSpan setTag(final String tag, final String value) {
    context().setTag(tag, (Object) value);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#setTag(java.lang.String, boolean)
   */
  @Override
  public final DDSpan setTag(final String tag, final boolean value) {
    context().setTag(tag, (Object) value);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#setTag(java.lang.String, java.lang.Number)
   */
  @Override
  public final DDSpan setTag(final String tag, final Number value) {
    context().setTag(tag, (Object) value);
    return this;
  }

  @Override
  public <T> Span setTag(final Tag<T> tag, final T value) {
    context().setTag(tag.getKey(), value);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#context()
   */
  @Override
  public final DDSpanContext context() {
    return context;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#getBaggageItem(java.lang.String)
   */
  @Override
  public final String getBaggageItem(final String key) {
    return context.getBaggageItem(key);
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#setBaggageItem(java.lang.String, java.lang.String)
   */
  @Override
  public final DDSpan setBaggageItem(final String key, final String value) {
    context.setBaggageItem(key, value);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#setOperationName(java.lang.String)
   */
  @Override
  public final DDSpan setOperationName(final String operationName) {
    context().setOperationName(operationName);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#log(java.util.Map)
   */
  @Override
  public final DDSpan log(final Map<String, ?> map) {
    logHandler.log(map, this);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#log(long, java.util.Map)
   */
  @Override
  public final DDSpan log(final long l, final Map<String, ?> map) {
    logHandler.log(l, map, this);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#log(java.lang.String)
   */
  @Override
  public final DDSpan log(final String s) {
    logHandler.log(s, this);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#log(long, java.lang.String)
   */
  @Override
  public final DDSpan log(final long l, final String s) {
    logHandler.log(l, s, this);
    return this;
  }

  @Override
  public final DDSpan setServiceName(final String serviceName) {
    context().setServiceName(serviceName);
    return this;
  }

  @Override
  public final DDSpan setResourceName(final String resourceName) {
    context().setResourceName(resourceName);
    return this;
  }

  /**
   * Set the sampling priority of the root span of this span's trace
   *
   * <p>Has no effect if the span priority has been propagated (injected or extracted).
   */
  @Override
  public final DDSpan setSamplingPriority(final int newPriority) {
    context().setSamplingPriority(newPriority);
    return this;
  }

  @Override
  public final DDSpan setSpanType(final String type) {
    context().setSpanType(type);
    return this;
  }

  // Getters

  /**
   * Meta merges baggage and tags (stringified values)
   *
   * @return merged context baggage and tags
   */
  public Map<String, String> getMeta() {
    final Map<String, String> meta = new HashMap<>();
    for (final Map.Entry<String, String> entry : context().getBaggageItems().entrySet()) {
      meta.put(entry.getKey(), entry.getValue());
    }
    for (final Map.Entry<String, Object> entry : getTags().entrySet()) {
      meta.put(entry.getKey(), String.valueOf(entry.getValue()));
    }
    return meta;
  }

  /**
   * Span metrics.
   *
   * @return metrics for this span
   */
  public Map<String, Number> getMetrics() {
    return context.getMetrics();
  }

  @Override
  public long getStartTime() {
    return startTimeNano > 0 ? startTimeNano : TimeUnit.MICROSECONDS.toNanos(startTimeMicro);
  }

  @Override
  public long getDurationNano() {
    return durationNano.get();
  }

  @Override
  public String getServiceName() {
    return context.getServiceName();
  }

  public BigInteger getTraceId() {
    return context.getTraceId();
  }

  public BigInteger getSpanId() {
    return context.getSpanId();
  }

  public BigInteger getParentId() {
    return context.getParentId();
  }

  @Override
  public String getResourceName() {
    return context.getResourceName();
  }

  @Override
  public String getOperationName() {
    return context.getOperationName();
  }

  @Override
  public Integer getSamplingPriority() {
    final int samplingPriority = context.getSamplingPriority();
    if (samplingPriority == PrioritySampling.UNSET) {
      return null;
    } else {
      return samplingPriority;
    }
  }

  @Override
  public String getSpanType() {
    return context.getSpanType();
  }

  @Override
  public Map<String, Object> getTags() {
    return context().getTags();
  }

  public String getType() {
    return context.getSpanType();
  }

  @Override
  public Boolean isError() {
    return context.getErrorFlag();
  }

  public int getError() {
    return context.getErrorFlag() ? 1 : 0;
  }

  @Override
  public String toString() {
    return new StringBuilder()
        .append(context.toString())
        .append(", duration_ns=")
        .append(durationNano)
        .toString();
  }
}
