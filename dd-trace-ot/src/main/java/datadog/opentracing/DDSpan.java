package datadog.opentracing;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import datadog.trace.api.DDTags;
import datadog.trace.common.sampling.PrioritySampling;
import datadog.trace.common.util.Clock;
import io.opentracing.Span;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
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
public class DDSpan implements Span {

  /** The context attached to the span */
  private final DDSpanContext context;

  /** Creation time of the span in microseconds. Must be greater than zero. */
  private final long startTimeMicro;

  /** Creation time of span in system relative nanotime (may be negative) */
  private final long startTimeNano;

  /**
   * The duration in nanoseconds computed using the startTimeMicro or startTimeNano. Span is
   * considered finished when this is set.
   */
  private final AtomicLong durationNano = new AtomicLong();

  /** Implementation detail. Stores the weak reference to this span. Used by TraceCollection. */
  volatile WeakReference<DDSpan> ref;

  /**
   * Spans should be constructed using the builder, not by calling the constructor directly.
   *
   * @param timestampMicro if greater than zero, use this time instead of the current time
   * @param context the context used for the span
   */
  DDSpan(final long timestampMicro, final DDSpanContext context) {

    this.context = context;

    // record the start time in nano (current milli + nano delta)
    if (timestampMicro <= 0L) {
      this.startTimeMicro = Clock.currentMicroTime();
      this.startTimeNano = Clock.currentNanoTicks();
    } else {
      this.startTimeMicro = timestampMicro;
      // timestamp might have come from an external clock, so don't bother with nanotime.
      this.startTimeNano = 0;
    }
    this.context.getTrace().registerSpan(this);
  }

  @JsonIgnore
  public boolean isFinished() {
    return durationNano.get() != 0;
  }

  @Override
  public final void finish() {
    if (startTimeNano != 0) {
      // no external clock was used, so we can rely on nanotime, but still ensure a min duration of 1.
      if (this.durationNano.compareAndSet(
          0, Math.max(1, Clock.currentNanoTicks() - startTimeNano))) {
        context.getTrace().addSpan(this);
      } else {
        log.debug("{} - already finished!", this);
      }
    } else {
      finish(Clock.currentMicroTime());
    }
  }

  @Override
  public final void finish(final long stoptimeMicros) {
    // Ensure that duration is at least 1.  Less than 1 is possible due to our use of system clock instead of nano time.
    if (this.durationNano.compareAndSet(
        0, Math.max(1, TimeUnit.MICROSECONDS.toNanos(stoptimeMicros - this.startTimeMicro)))) {
      context.getTrace().addSpan(this);
    } else {
      log.debug("{} - already finished!", this);
    }
  }

  /**
   * Check if the span is the root parent. It means that the traceId is the same as the spanId
   *
   * @return true if root, false otherwise
   */
  @JsonIgnore
  public final boolean isRootSpan() {
    return context.getParentId() == 0;
  }

  public void setErrorMeta(final Throwable error) {
    context.setErrorFlag(true);

    setTag(DDTags.ERROR_MSG, error.getMessage());
    setTag(DDTags.ERROR_TYPE, error.getClass().getName());

    final StringWriter errorString = new StringWriter();
    error.printStackTrace(new PrintWriter(errorString));
    setTag(DDTags.ERROR_STACK, errorString.toString());
  }

  private boolean extractError(final Map<String, ?> map) {
    if (map.get(ERROR_OBJECT) instanceof Throwable) {
      final Throwable error = (Throwable) map.get(ERROR_OBJECT);
      setErrorMeta(error);
      return true;
    }
    return false;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#setTag(java.lang.String, java.lang.String)
   */
  @Override
  public final Span setTag(final String tag, final String value) {
    this.context().setTag(tag, (Object) value);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#setTag(java.lang.String, boolean)
   */
  @Override
  public final Span setTag(final String tag, final boolean value) {
    this.context().setTag(tag, (Object) value);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#setTag(java.lang.String, java.lang.Number)
   */
  @Override
  public final Span setTag(final String tag, final Number value) {
    this.context().setTag(tag, (Object) value);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#context()
   */
  @Override
  public final DDSpanContext context() {
    return this.context;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#getBaggageItem(java.lang.String)
   */
  @Override
  public final String getBaggageItem(final String key) {
    return this.context.getBaggageItem(key);
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#setBaggageItem(java.lang.String, java.lang.String)
   */
  @Override
  public final DDSpan setBaggageItem(final String key, final String value) {
    this.context.setBaggageItem(key, value);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#setOperationName(java.lang.String)
   */
  @Override
  public final DDSpan setOperationName(final String operationName) {
    this.context().setOperationName(operationName);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#log(java.util.Map)
   */
  @Override
  public final DDSpan log(final Map<String, ?> map) {
    if (!extractError(map)) {
      log.debug("`log` method is not implemented. Doing nothing");
    }
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#log(long, java.util.Map)
   */
  @Override
  public final DDSpan log(final long l, final Map<String, ?> map) {
    if (!extractError(map)) {
      log.debug("`log` method is not implemented. Doing nothing");
    }
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#log(java.lang.String)
   */
  @Override
  public final DDSpan log(final String s) {
    log.debug("`log` method is not implemented. Provided log: {}", s);
    return this;
  }

  /* (non-Javadoc)
   * @see io.opentracing.BaseSpan#log(long, java.lang.String)
   */
  @Override
  public final DDSpan log(final long l, final String s) {
    log.debug("`log` method is not implemented. Provided log: {}", s);
    return this;
  }

  public final DDSpan setServiceName(final String serviceName) {
    this.context().setServiceName(serviceName);
    return this;
  }

  public final DDSpan setResourceName(final String resourceName) {
    this.context().setResourceName(resourceName);
    return this;
  }

  /**
   * Set the sampling priority of the span.
   *
   * <p>Has no effect if the span priority has been propagated (injected or extracted).
   */
  public final DDSpan setSamplingPriority(final int newPriority) {
    this.context().setSamplingPriority(newPriority);
    return this;
  }

  public final DDSpan setSpanType(final String type) {
    this.context().setSpanType(type);
    return this;
  }

  // Getters and JSON serialisation instructions

  /**
   * Meta merges baggage and tags (stringified values)
   *
   * @return merged context baggage and tags
   */
  @JsonGetter
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

  @JsonGetter("start")
  public long getStartTime() {
    return startTimeMicro * 1000L;
  }

  @JsonGetter("duration")
  public long getDurationNano() {
    return durationNano.get();
  }

  @JsonGetter("service")
  public String getServiceName() {
    return context.getServiceName();
  }

  @JsonGetter("trace_id")
  public long getTraceId() {
    return context.getTraceId();
  }

  @JsonGetter("span_id")
  public long getSpanId() {
    return context.getSpanId();
  }

  @JsonGetter("parent_id")
  public long getParentId() {
    return context.getParentId();
  }

  @JsonGetter("resource")
  public String getResourceName() {
    return context.getResourceName();
  }

  @JsonGetter("name")
  public String getOperationName() {
    return context.getOperationName();
  }

  @JsonGetter("sampling_priority")
  @JsonInclude(Include.NON_NULL)
  public Integer getSamplingPriority() {
    final int samplingPriority = context.getSamplingPriority();
    if (samplingPriority == PrioritySampling.UNSET) {
      return null;
    } else {
      return samplingPriority;
    }
  }

  @JsonIgnore
  public Map<String, Object> getTags() {
    return this.context().getTags();
  }

  @JsonGetter
  public String getType() {
    return context.getSpanType();
  }

  @JsonGetter
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
