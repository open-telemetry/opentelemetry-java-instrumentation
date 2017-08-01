package com.datadoghq.trace;

import com.datadoghq.trace.integration.AbstractDecorator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Maps;
import io.opentracing.tag.Tags;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * SpanContext represents Span state that must propagate to descendant Spans and across process
 * boundaries.
 *
 * <p>SpanContext is logically divided into two pieces: (1) the user-level "Baggage" that propagates
 * across Span boundaries and (2) any Datadog fields that are needed to identify or contextualize
 * the associated Span instance
 */
public class DDSpanContext implements io.opentracing.SpanContext {

  public static final String LANGUAGE_FIELDNAME = "lang";
  // Opentracing attributes
  private final long traceId;
  private final long spanId;
  private final long parentId;
  private final String threadName = Thread.currentThread().getName();
  private final long threadId = Thread.currentThread().getId();
  /** The collection of all span related to this one */
  private final Queue<DDBaseSpan<?>> trace;

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
  // Others attributes
  /** Tags are associated to the current span, they will not propagate to the children span */
  private Map<String, Object> tags;

  public DDSpanContext(
      final long traceId,
      final long spanId,
      final long parentId,
      final String serviceName,
      final String operationName,
      final String resourceName,
      final Map<String, String> baggageItems,
      final boolean errorFlag,
      final String spanType,
      final Map<String, Object> tags,
      final Queue<DDBaseSpan<?>> trace,
      final DDTracer tracer) {

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
    this.errorFlag = errorFlag;
    this.spanType = spanType;

    this.tags = tags;

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

  public void setBaggageItem(final String key, final String value) {
    if (this.baggageItems.isEmpty()) {
      this.baggageItems = new HashMap<String, String>();
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
  public Queue<DDBaseSpan<?>> getTrace() {
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
   * @param value the value of the value
   */
  public synchronized void setTag(final String tag, final Object value) {
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
      this.tags = new HashMap<String, Object>();
    }
    this.tags.put(tag, value);

    //Call decorators
    final List<AbstractDecorator> decorators = tracer.getSpanContextDecorators(tag);
    if (decorators != null) {
      for (final AbstractDecorator decorator : decorators) {
        decorator.afterSetTag(this, tag, value);
      }
    }
    //Error management
    if (Tags.ERROR.getKey().equals(tag) && Boolean.TRUE.equals(value)) {
      this.errorFlag = true;
    }
  }

  public synchronized Map<String, Object> getTags() {
    if (tags.isEmpty()) {
      tags = Maps.newHashMapWithExpectedSize(2);
    }
    tags.put(DDTags.THREAD_NAME, threadName);
    tags.put(DDTags.THREAD_ID, threadId);
    return Collections.unmodifiableMap(tags);
  }

  @Override
  public String toString() {
    return new StringBuilder()
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
        .append(getResourceName())
        .toString();
  }

  public String getOperationName() {
    return operationName;
  }

  public void setOperationName(final String operationName) {
    this.operationName = operationName;
  }
}
