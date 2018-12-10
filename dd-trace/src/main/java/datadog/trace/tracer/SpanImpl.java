package datadog.trace.tracer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class SpanImpl implements Span {
  private final Clock clock = null;
  private final Trace trace = null;
  // See See https://docs.datadoghq.com/api/?lang=python#tracing
  // Required attributes to report to datadog.
  private final SpanContext context = null;
  /* Span name. May not exceed 100 characters. */
  private final String name = "";
  /* Span resource (e.g. http endpoint). May not exceed 5000 characters. */
  private final String resource = "";
  /* Span service. May not exceed 100 characters. */
  private final String service = "";
  /* The start time of the request in nanoseconds from the unix epoch. */
  private final long startEpochNano = -1;
  /* Duration of the span in nanoseconds */
  private final AtomicLong durationNano = new AtomicLong(-1);
  // optional attributes to report to datadog
  /* The type of the span (web, db, etc). See DDSpanTypes. */
  private final String type = null;
  /* Marks the span as having an error. */
  private final boolean isErrored = false;
  /* Additional key-value pairs for a span. */
  private final Map<String, Object> meta = null;

  /**
   * Create a span with a start time of the current timestamp.
   *
   * @param trace The trace to associate this span with
   * @param parentContext identifies the parent of this span. May be null.
   * @param clock The clock to use to measure the span's duration.
   * @param interceptors interceptors to run on the span
   */
  SpanImpl(
      final TraceImpl trace,
      final SpanContext parentContext,
      final Clock clock,
      List<Interceptor> interceptors) {}

  /**
   * Create a span with the a specific start timestamp.
   *
   * @param trace The trace to associate this span with
   * @param parentContext identifies the parent of this span. May be null.
   * @param clock The clock to use to measure the span's duration.
   * @param interceptors interceptors to run on the span
   * @param startTimestampNanoseconds Epoch time in nanoseconds when this span started.
   */
  SpanImpl(
      final TraceImpl trace,
      final SpanContext parentContext,
      final Clock clock,
      List<Interceptor> interceptors,
      final long startTimestampNanoseconds) {}

  @Override
  public Trace getTrace() {
    return null;
  }

  @Override
  public void finish() {}

  @Override
  public void finish(long finishTimestampNanoseconds) {}

  @Override
  public boolean isFinished() {
    return false;
  }

  @Override
  public void attachThrowable(Throwable t) {}

  @Override
  public void setError(boolean isErrored) {}

  @Override
  public SpanContext getContext() {
    return null;
  }

  @Override
  public Object getMeta(String key) {
    return null;
  }

  @Override
  public void setMeta(String key, String value) {}

  @Override
  public void setMeta(String key, boolean value) {}

  @Override
  public void setMeta(String key, Number value) {}

  @Override
  public String getName() {
    return null;
  }

  @Override
  public void setName(String newName) {}

  @Override
  public String getResource() {
    return null;
  }

  @Override
  public void setResource(String newResource) {}

  @Override
  public String getService() {
    return null;
  }

  @Override
  public void setService(String newService) {}

  @Override
  public String getType() {
    return null;
  }

  @Override
  public void setType(String newType) {}

  @Override
  public boolean isErrored() {
    return false;
  }
}
