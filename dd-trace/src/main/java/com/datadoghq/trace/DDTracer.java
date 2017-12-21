package com.datadoghq.trace;

import com.datadoghq.trace.integration.AbstractDecorator;
import com.datadoghq.trace.propagation.Codec;
import com.datadoghq.trace.propagation.HTTPCodec;
import com.datadoghq.trace.resolver.DDDecoratorsFactory;
import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.sampling.Sampler;
import com.datadoghq.trace.writer.DDAgentWriter;
import com.datadoghq.trace.writer.Writer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.opentracing.ActiveSpan;
import io.opentracing.ActiveSpanSource;
import io.opentracing.BaseSpan;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.util.ThreadLocalActiveSpanSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;

/** DDTracer makes it easy to send traces and span to DD using the OpenTracing integration. */
@Slf4j
public class DDTracer extends ThreadLocalActiveSpanSource implements io.opentracing.Tracer {

  public static final String UNASSIGNED_DEFAULT_SERVICE_NAME = "unnamed-java-app";
  public static final Writer UNASSIGNED_WRITER = new DDAgentWriter();
  public static final Sampler UNASSIGNED_SAMPLER = new AllSampler();

  /** Writer is an charge of reporting traces and spans to the desired endpoint */
  final Writer writer;
  /** Sampler defines the sampling policy in order to reduce the number of traces for instance */
  final Sampler sampler;

  /** Default service name if none provided on the trace or span */
  final String serviceName;

  /** Span context decorators */
  private final Map<String, List<AbstractDecorator>> spanContextDecorators = new HashMap<>();

  private final CodecRegistry registry;
  private final Map<String, Service> services = new HashMap<>();

  /** By default, report to local agent and collect all traces. */
  public DDTracer() {
    this(new DDTraceConfig());
  }

  public DDTracer(final String serviceName) {
    this(new DDTraceConfig(serviceName));
  }

  public DDTracer(final Properties config) {
    this(
        config.getProperty(DDTraceConfig.SERVICE_NAME),
        Writer.Builder.forConfig(config),
        Sampler.Builder.forConfig(config));
    log.debug("Using config: {}", config);

    // Create decorators from resource files
    final List<AbstractDecorator> decorators = DDDecoratorsFactory.createFromResources();
    for (final AbstractDecorator decorator : decorators) {
      log.debug("Loading decorator: {}", decorator.getClass().getSimpleName());
      addDecorator(decorator);
    }
  }

  public DDTracer(final String serviceName, final Writer writer, final Sampler sampler) {
    this.serviceName = serviceName;
    this.writer = writer;
    this.writer.start();
    this.sampler = sampler;
    registry = new CodecRegistry();
    registry.register(Format.Builtin.HTTP_HEADERS, new HTTPCodec());
    registry.register(Format.Builtin.TEXT_MAP, new HTTPCodec());
    log.info("New instance: {}", this);
  }

  public DDTracer(final Writer writer) {
    this(writer, new AllSampler());
  }

  public DDTracer(final Writer writer, final Sampler sampler) {
    this(UNASSIGNED_DEFAULT_SERVICE_NAME, writer, sampler);
  }

  /**
   * Returns the list of span context decorators
   *
   * @return the list of span context decorators
   */
  public List<AbstractDecorator> getSpanContextDecorators(final String tag) {
    return spanContextDecorators.get(tag);
  }

  /**
   * Add a new decorator in the list ({@link AbstractDecorator})
   *
   * @param decorator The decorator in the list
   */
  public void addDecorator(final AbstractDecorator decorator) {

    List<AbstractDecorator> list = spanContextDecorators.get(decorator.getMatchingTag());
    if (list == null) {
      list = new ArrayList<>();
    }
    list.add(decorator);

    spanContextDecorators.put(decorator.getMatchingTag(), list);
  }

  @Override
  public DDSpanBuilder buildSpan(final String operationName) {
    return new DDSpanBuilder(operationName, this);
  }

  @Override
  public <T> void inject(final SpanContext spanContext, final Format<T> format, final T carrier) {

    final Codec<T> codec = registry.get(format);
    if (codec == null) {
      log.warn("Unsupported format for propagation - {}", format.getClass().getName());
    } else {
      codec.inject((DDSpanContext) spanContext, carrier);
    }
  }

  @Override
  public <T> SpanContext extract(final Format<T> format, final T carrier) {

    final Codec<T> codec = registry.get(format);
    if (codec == null) {
      log.warn("Unsupported format for propagation - {}", format.getClass().getName());
    } else {
      return codec.extract(carrier);
    }
    return null;
  }

  /**
   * We use the sampler to know if the trace has to be reported/written. The sampler is called on
   * the first span (root span) of the trace. If the trace is marked as a sample, we report it.
   *
   * @param trace a list of the spans related to the same trace
   */
  public void write(final Queue<DDBaseSpan<?>> trace) {
    if (trace.isEmpty()) {
      return;
    }
    if (this.sampler.sample(trace.peek())) {
      this.writer.write(new ArrayList<>(trace));
    }
  }

  public void close() {
    writer.close();
  }

  @Override
  public String toString() {
    return "DDTracer-"
        + Integer.toHexString(hashCode())
        + "{ service-name="
        + serviceName
        + ", writer="
        + writer
        + ", sampler="
        + sampler
        + '}';
  }

  /**
   * Register additional information about a service. Service additional information are a Datadog
   * feature only. Services are reported through a specific Datadog endpoint.
   *
   * @param service additional service information
   */
  public void addServiceInfo(final Service service) {
    services.put(service.getName(), service);
    // Update the writer
    try {
      // We don't bother to send multiple times the list of services at this time
      writer.writeServices(services);
    } catch (final Throwable ex) {
      log.warn("Failed to report additional service information, reason: {}", ex.getMessage());
    }
  }

  /**
   * Return the list of additional service information registered
   *
   * @return the list of additional service information
   */
  @JsonIgnore
  public Map<String, Service> getServiceInfo() {
    return services;
  }

  private static class CodecRegistry {

    private final Map<Format<?>, Codec<?>> codecs = new HashMap<>();

    <T> Codec<T> get(final Format<T> format) {
      return (Codec<T>) codecs.get(format);
    }

    public <T> void register(final Format<T> format, final Codec<T> codec) {
      codecs.put(format, codec);
    }
  }

  /** Spans are built using this builder */
  public class DDSpanBuilder implements SpanBuilder {
    private final ActiveSpanSource spanSource;

    /** Each span must have an operationName according to the opentracing specification */
    private final String operationName;

    // Builder attributes
    private Map<String, Object> tags = Collections.emptyMap();
    private long timestamp;
    private SpanContext parent;
    private String serviceName;
    private String resourceName;
    private boolean errorFlag;
    private String spanType;
    private boolean ignoreActiveSpan = false;

    public DDSpanBuilder(final String operationName, final ActiveSpanSource spanSource) {
      this.operationName = operationName;
      this.spanSource = spanSource;
    }

    @Override
    public SpanBuilder ignoreActiveSpan() {
      this.ignoreActiveSpan = true;
      return this;
    }

    private DDSpan startSpan() {
      return new DDSpan(this.timestamp, buildSpanContext());
    }

    @Override
    public ActiveSpan startActive() {
      final DDSpan span = startSpan();
      final ActiveSpan activeSpan = spanSource.makeActive(span);
      log.debug("Starting a new active span: {}", span);
      return activeSpan;
    }

    @Override
    public DDSpan startManual() {
      final DDSpan span = startSpan();
      log.debug("Starting a new manual span: {}", span);
      return span;
    }

    @Override
    @Deprecated
    public DDSpan start() {
      return startManual();
    }

    @Override
    public DDSpanBuilder withTag(final String tag, final Number number) {
      return withTag(tag, (Object) number);
    }

    @Override
    public DDSpanBuilder withTag(final String tag, final String string) {
      if (tag.equals(DDTags.SERVICE_NAME)) {
        return withServiceName(string);
      } else if (tag.equals(DDTags.RESOURCE_NAME)) {
        return withResourceName(string);
      } else if (tag.equals(DDTags.SPAN_TYPE)) {
        return withSpanType(string);
      } else {
        return withTag(tag, (Object) string);
      }
    }

    @Override
    public DDSpanBuilder withTag(final String tag, final boolean bool) {
      return withTag(tag, (Object) bool);
    }

    @Override
    public DDSpanBuilder withStartTimestamp(final long timestampMillis) {
      this.timestamp = timestampMillis;
      return this;
    }

    public DDSpanBuilder withServiceName(final String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    public DDSpanBuilder withResourceName(final String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    public DDSpanBuilder withErrorFlag() {
      this.errorFlag = true;
      return this;
    }

    public DDSpanBuilder withSpanType(final String spanType) {
      this.spanType = spanType;
      return this;
    }

    public Iterable<Map.Entry<String, String>> baggageItems() {
      if (parent == null) {
        return Collections.emptyList();
      }
      return parent.baggageItems();
    }

    @Override
    public DDSpanBuilder asChildOf(final BaseSpan<?> span) {
      return asChildOf(span == null ? null : span.context());
    }

    @Override
    public DDSpanBuilder asChildOf(final SpanContext spanContext) {
      this.parent = spanContext;
      return this;
    }

    @Override
    public DDSpanBuilder addReference(final String referenceType, final SpanContext spanContext) {
      log.debug("`addReference` method is not implemented. Doing nothing");
      return this;
    }

    // Private methods
    private DDSpanBuilder withTag(final String tag, final Object value) {
      if (this.tags.isEmpty()) {
        this.tags = new HashMap<>();
      }
      this.tags.put(tag, value);
      return this;
    }

    private long generateNewId() {
      // Ensure the generated ID is in a valid range:
      return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    /**
     * Build the SpanContext, if the actual span has a parent, the following attributes must be
     * propagated: - ServiceName - Baggage - Trace (a list of all spans related) - SpanType
     *
     * @return the context
     */
    private DDSpanContext buildSpanContext() {
      final long traceId;
      final long spanId = generateNewId();
      final long parentSpanId;
      final Map<String, String> baggage;
      final Queue<DDBaseSpan<?>> parentTrace;

      final DDSpanContext context;
      SpanContext parentContext = this.parent;
      if (parentContext == null && !ignoreActiveSpan) {
        // use the ActiveSpan as parent unless overridden or ignored.
        final ActiveSpan activeSpan = activeSpan();
        if (activeSpan != null) parentContext = activeSpan.context();
      }

      if (parentContext instanceof DDSpanContext) {
        final DDSpanContext ddsc = (DDSpanContext) parentContext;
        traceId = ddsc.getTraceId();
        parentSpanId = ddsc.getSpanId();
        baggage = ddsc.getBaggageItems();
        parentTrace = ddsc.getTrace();

        if (this.serviceName == null) this.serviceName = ddsc.getServiceName();
        if (this.spanType == null) this.spanType = ddsc.getSpanType();
      } else {
        traceId = generateNewId();
        parentSpanId = 0L;
        baggage = null;
        parentTrace = null;
      }

      if (serviceName == null) {
        serviceName = DDTracer.this.serviceName;
      }

      final String operationName =
          this.operationName != null ? this.operationName : this.resourceName;

      // this.operationName, this.tags,

      // some attributes are inherited from the parent
      context =
          new DDSpanContext(
              traceId,
              spanId,
              parentSpanId,
              serviceName,
              operationName,
              this.resourceName,
              baggage,
              errorFlag,
              spanType,
              this.tags,
              parentTrace,
              DDTracer.this);

      return context;
    }
  }
}
