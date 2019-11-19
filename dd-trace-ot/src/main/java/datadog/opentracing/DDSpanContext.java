package datadog.opentracing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import datadog.opentracing.decorators.AbstractDecorator;
import datadog.trace.api.DDTags;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * SpanContext represents Span state that must propagate to descendant Spans and across process
 * boundaries.
 *
 * <p>SpanContext includes Datadog fields that are needed to identify or contextualize the
 * associated Span instance
 */
@Slf4j
public class DDSpanContext implements io.opentracing.SpanContext {

  private static final Map<String, Number> EMPTY_METRICS = Collections.emptyMap();

  // Shared with other span contexts
  /** For technical reasons, the ref to the original tracer */
  private final DDTracer tracer;

  /** The collection of all span related to this one */
  private final PendingTrace trace;

  // Not Shared with other span contexts
  private final BigInteger traceId;
  private final BigInteger spanId;
  private final BigInteger parentId;

  /** Tags are associated to the current span, they will not propagate to the children span */
  private final Map<String, Object> tags = new ConcurrentHashMap<>();

  /** The service name is required, otherwise the span are dropped by the agent */
  private volatile String serviceName;
  /** The resource associated to the service (server_web, database, etc.) */
  private volatile String resourceName;
  /** Each span have an operation name describing the current span */
  private volatile String operationName;
  /** The type of the span. If null, the Datadog Agent will report as a custom */
  private volatile String spanType;
  /** True indicates that the span reports an error */
  private volatile boolean errorFlag;
  /** Metrics on the span */
  private final AtomicReference<Map<String, Number>> metrics = new AtomicReference<>();

  // Additional Metadata
  private final String threadName = Thread.currentThread().getName();
  private final long threadId = Thread.currentThread().getId();

  public DDSpanContext(
      final BigInteger traceId,
      final BigInteger spanId,
      final BigInteger parentId,
      final String serviceName,
      final String operationName,
      final String resourceName,
      final boolean errorFlag,
      final String spanType,
      final Map<String, Object> tags,
      final PendingTrace trace,
      final DDTracer tracer) {

    assert tracer != null;
    assert trace != null;
    this.tracer = tracer;
    this.trace = trace;

    assert traceId != null;
    assert spanId != null;
    assert parentId != null;
    this.traceId = traceId;
    this.spanId = spanId;
    this.parentId = parentId;

    if (tags != null) {
      this.tags.putAll(tags);
    }

    this.serviceName = serviceName;
    this.operationName = operationName;
    this.resourceName = resourceName;
    this.errorFlag = errorFlag;
    this.spanType = spanType;

    this.tags.put(DDTags.THREAD_NAME, threadName);
    this.tags.put(DDTags.THREAD_ID, threadId);
  }

  public BigInteger getTraceId() {
    return traceId;
  }

  @Override
  public String toTraceId() {
    return traceId.toString();
  }

  public BigInteger getParentId() {
    return parentId;
  }

  public BigInteger getSpanId() {
    return spanId;
  }

  @Override
  public String toSpanId() {
    return spanId.toString();
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(final String serviceName) {
    this.serviceName = serviceName;
  }

  public String getResourceName() {
    return resourceName == null || resourceName.isEmpty() ? operationName : resourceName;
  }

  public void setResourceName(final String resourceName) {
    this.resourceName = resourceName;
  }

  public String getOperationName() {
    return operationName;
  }

  public void setOperationName(final String operationName) {
    this.operationName = operationName;
  }

  public boolean getErrorFlag() {
    return errorFlag;
  }

  public void setErrorFlag(final boolean errorFlag) {
    this.errorFlag = errorFlag;
  }

  public String getSpanType() {
    return spanType;
  }

  public void setSpanType(final String spanType) {
    this.spanType = spanType;
  }

  /* (non-Javadoc)
   * @see io.opentracing.SpanContext#baggageItems()
   */
  @Override
  public Iterable<Map.Entry<String, String>> baggageItems() {
    return Collections.emptyList();
  }

  @JsonIgnore
  public PendingTrace getTrace() {
    return trace;
  }

  @JsonIgnore
  public DDTracer getTracer() {
    return tracer;
  }

  public Map<String, Number> getMetrics() {
    final Map<String, Number> metrics = this.metrics.get();
    return metrics == null ? EMPTY_METRICS : metrics;
  }

  public void setMetric(final String key, final Number value) {
    if (metrics.get() == null) {
      metrics.compareAndSet(null, new ConcurrentHashMap<String, Number>());
    }
    if (value instanceof Float) {
      metrics.get().put(key, value.doubleValue());
    } else {
      metrics.get().put(key, value);
    }
  }
  /**
   * Add a tag to the span. Tags are not propagated to the children
   *
   * @param tag the tag-name
   * @param value the value of the tag. tags with null values are ignored.
   */
  public synchronized void setTag(final String tag, final Object value) {
    if (value == null || (value instanceof String && ((String) value).isEmpty())) {
      tags.remove(tag);
      return;
    }

    boolean addTag = true;

    // Call decorators
    final List<AbstractDecorator> decorators = tracer.getSpanContextDecorators(tag);
    if (decorators != null) {
      for (final AbstractDecorator decorator : decorators) {
        try {
          addTag &= decorator.shouldSetTag(this, tag, value);
        } catch (final Throwable ex) {
          log.debug(
              "Could not decorate the span decorator={}: {}",
              decorator.getClass().getSimpleName(),
              ex.getMessage());
        }
      }
    }

    if (addTag) {
      tags.put(tag, value);
    }
  }

  public synchronized Map<String, Object> getTags() {
    return Collections.unmodifiableMap(tags);
  }

  @Override
  public String toString() {
    final StringBuilder s =
        new StringBuilder()
            .append("DDSpan [ t_id=")
            .append(traceId)
            .append(", s_id=")
            .append(spanId)
            .append(", p_id=")
            .append(parentId)
            .append("] trace=")
            .append(getServiceName())
            .append("/")
            .append(getOperationName())
            .append("/")
            .append(getResourceName())
            .append(" metrics=")
            .append(new TreeMap(getMetrics()));
    if (errorFlag) {
      s.append(" *errored*");
    }
    if (tags != null) {
      s.append(" tags=").append(new TreeMap(tags));
    }
    return s.toString();
  }
}
