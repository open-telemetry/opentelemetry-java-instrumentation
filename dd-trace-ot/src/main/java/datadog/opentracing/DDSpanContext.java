package datadog.opentracing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Maps;
import datadog.opentracing.decorators.AbstractDecorator;
import datadog.trace.api.DDTags;
import datadog.trace.common.sampling.PrioritySampling;
import io.opentracing.tag.Tags;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.extern.slf4j.Slf4j;

/**
 * SpanContext represents Span state that must propagate to descendant Spans and across process
 * boundaries.
 *
 * <p>SpanContext is logically divided into two pieces: (1) the user-level "Baggage" that propagates
 * across Span boundaries and (2) any Datadog fields that are needed to identify or contextualize
 * the associated Span instance
 */
@Slf4j
public class DDSpanContext implements io.opentracing.SpanContext {

  // Opentracing attributes
  private final long traceId;
  private final long spanId;
  private final long parentId;
  private final String threadName = Thread.currentThread().getName();
  private final long threadId = Thread.currentThread().getId();
  /** The collection of all span related to this one */
  private final Queue<DDSpan> trace;

  // DD attributes
  /** For technical reasons, the ref to the original tracer */
  private final DDTracer tracer;

  private Map<String, String> baggageItems;
  /** The service name is required, otherwise the span are dropped by the agent */
  private String serviceName;
  /** The resource associated to the service (server_web, database, etc.) */
  private String resourceName;
  /** True indicates that the span reports an error */
  private boolean errorFlag;
  /** The type of the span. If null, the Datadog Agent will report as a custom */
  private String spanType;
  /** Each span have an operation name describing the current span */
  private String operationName;
  /** The sampling priority of the trace */
  private volatile int samplingPriority = PrioritySampling.UNSET;
  /** When true, the samplingPriority cannot be changed. */
  private volatile boolean samplingPriorityLocked = false;
  // Others attributes
  /** Tags are associated to the current span, they will not propagate to the children span */
  private Map<String, Object> tags;

  @JsonIgnore public final boolean useRefCounting;

  public DDSpanContext(
      final long traceId,
      final long spanId,
      final long parentId,
      final String serviceName,
      final String operationName,
      final String resourceName,
      final int samplingPriority,
      final Map<String, String> baggageItems,
      final boolean errorFlag,
      final String spanType,
      final Map<String, Object> tags,
      final Queue<DDSpan> trace,
      final DDTracer tracer,
      final boolean useRefCounting) {

    this.traceId = traceId;
    this.spanId = spanId;
    this.parentId = parentId;

    if (baggageItems == null) {
      this.baggageItems = Collections.emptyMap();
    } else {
      this.baggageItems = baggageItems;
    }

    this.serviceName = serviceName;
    this.operationName = operationName;
    this.resourceName = resourceName;
    this.samplingPriority = samplingPriority;
    this.errorFlag = errorFlag;
    this.spanType = spanType;

    this.tags = tags;

    this.useRefCounting = useRefCounting;

    if (trace == null) {
      // TODO: figure out better concurrency model.
      this.trace = new ConcurrentLinkedQueue<>();
    } else {
      this.trace = trace;
    }

    this.tracer = tracer;
  }

  public long getTraceId() {
    return this.traceId;
  }

  public long getParentId() {
    return this.parentId;
  }

  public long getSpanId() {
    return this.spanId;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(final String serviceName) {
    this.serviceName = serviceName;
  }

  public String getResourceName() {
    return this.resourceName == null || this.resourceName.isEmpty()
        ? this.operationName
        : this.resourceName;
  }

  public void setResourceName(final String resourceName) {
    this.resourceName = resourceName;
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

  public void setSamplingPriority(final int newPriority) {
    if (samplingPriorityLocked) {
      log.warn(
          "samplingPriority locked at {}. Refusing to set to {}", samplingPriority, newPriority);
    } else {
      synchronized (this) {
        // sync with lockSamplingPriority
        this.samplingPriority = newPriority;
      }
    }
  }

  public int getSamplingPriority() {
    return samplingPriority;
  }

  /**
   * Prevent future changes to the context's sampling priority.
   *
   * <p>Used when a span is extracted or injected for propagation.
   *
   * <p>Has no effect if the sampling priority is unset.
   *
   * @return true if the sampling priority was locked.
   */
  public boolean lockSamplingPriority() {
    if (!samplingPriorityLocked) {
      synchronized (this) {
        // sync with setSamplingPriority
        if (samplingPriority == PrioritySampling.UNSET) {
          log.debug("{} : refusing to lock unset samplingPriority", this);
        } else {
          this.samplingPriorityLocked = true;
          log.debug("{} : locked samplingPriority to {}", this, this.samplingPriority);
        }
      }
    }
    return samplingPriorityLocked;
  }

  public void setBaggageItem(final String key, final String value) {
    if (this.baggageItems.isEmpty()) {
      this.baggageItems = new HashMap<>();
    }
    this.baggageItems.put(key, value);
  }

  public String getBaggageItem(final String key) {
    return this.baggageItems.get(key);
  }

  public Map<String, String> getBaggageItems() {
    return baggageItems;
  }

  /* (non-Javadoc)
   * @see io.opentracing.SpanContext#baggageItems()
   */
  @Override
  public Iterable<Map.Entry<String, String>> baggageItems() {
    return this.baggageItems.entrySet();
  }

  @JsonIgnore
  public Queue<DDSpan> getTrace() {
    return this.trace;
  }

  @JsonIgnore
  public DDTracer getTracer() {
    return this.tracer;
  }

  /**
   * Add a tag to the span. Tags are not propagated to the children
   *
   * @param tag the tag-name
   * @param value the value of the tag. tags with null values are ignored.
   */
  public synchronized void setTag(final String tag, final Object value) {
    if (value == null) {
      tags.remove(tag);
      return;
    }

    if (tag.equals(DDTags.SERVICE_NAME)) {
      setServiceName(value.toString());
      return;
    } else if (tag.equals(DDTags.RESOURCE_NAME)) {
      setResourceName(value.toString());
      return;
    } else if (tag.equals(DDTags.SPAN_TYPE)) {
      setSpanType(value.toString());
      return;
    }

    if (this.tags.isEmpty()) {
      this.tags = new HashMap<>();
    }
    this.tags.put(tag, value);

    // Call decorators
    final List<AbstractDecorator> decorators = tracer.getSpanContextDecorators(tag);
    if (decorators != null && value != null) {
      for (final AbstractDecorator decorator : decorators) {
        try {
          decorator.afterSetTag(this, tag, value);
        } catch (final Throwable ex) {
          log.warn(
              "Could not decorate the span decorator={}: {}",
              decorator.getClass().getSimpleName(),
              ex.getMessage());
        }
      }
    }
    // Error management
    if (Tags.ERROR.getKey().equals(tag)
        && Boolean.TRUE.equals(value instanceof String ? Boolean.valueOf((String) value) : value)) {
      this.errorFlag = true;
    }
  }

  public synchronized Map<String, Object> getTags() {
    if (tags.isEmpty()) {
      tags = Maps.newHashMapWithExpectedSize(3);
    }
    tags.put(DDTags.THREAD_NAME, threadName);
    tags.put(DDTags.THREAD_ID, threadId);
    tags.put(DDTags.SPAN_TYPE, getSpanType());
    return Collections.unmodifiableMap(tags);
  }

  @Override
  public String toString() {
    final StringBuilder s =
        new StringBuilder()
            .append("Span [ t_id=")
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
            .append(getResourceName());
    if (getSamplingPriority() != PrioritySampling.UNSET) {
      s.append(" samplingPriority=").append(getSamplingPriority());
    }
    if (errorFlag) {
      s.append(" *errored*");
    }
    if (tags != null) {
      s.append(" tags=").append(new TreeMap(tags));
    }
    return s.toString();
  }

  public String getOperationName() {
    return operationName;
  }

  public void setOperationName(final String operationName) {
    this.operationName = operationName;
  }
}
