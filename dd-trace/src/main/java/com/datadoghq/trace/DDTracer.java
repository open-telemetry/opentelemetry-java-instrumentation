package com.datadoghq.trace;

import com.datadoghq.trace.integration.DDSpanContextDecorator;
import com.datadoghq.trace.propagation.Codec;
import com.datadoghq.trace.propagation.HTTPCodec;
import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.sampling.Sampler;
import com.datadoghq.trace.writer.LoggingWriter;
import com.datadoghq.trace.writer.Writer;
import io.opentracing.ActiveSpan;
import io.opentracing.ActiveSpanSource;
import io.opentracing.BaseSpan;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.util.ThreadLocalActiveSpanSource;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** DDTracer makes it easy to send traces and span to DD using the OpenTracing integration. */
public class DDTracer extends ThreadLocalActiveSpanSource implements io.opentracing.Tracer {

  public static final String JAVA_VERSION = System.getProperty("java.version", "unknown");
  public static final String CURRENT_VERSION;

  static {
    String version = DDTracer.class.getPackage().getImplementationVersion();
    CURRENT_VERSION = version != null ? version : "unknown";
  }

  /** Writer is an charge of reporting traces and spans to the desired endpoint */
  private Writer writer;
  /** Sampler defines the sampling policy in order to reduce the number of traces for instance */
  private final Sampler sampler;

  /** Default service name if none provided on the trace or span */
  private final String defaultServiceName;

  /** Span context decorators */
  private final Map<String, List<DDSpanContextDecorator>> spanContextDecorators =
      new HashMap<String, List<DDSpanContextDecorator>>();

  private static final Logger logger = LoggerFactory.getLogger(DDTracer.class);
  private final CodecRegistry registry;

  public static final String UNASSIGNED_DEFAULT_SERVICE_NAME = "unnamed-java-app";
  public static final Writer UNASSIGNED_WRITER = new LoggingWriter();
  public static final Sampler UNASSIGNED_SAMPLER = new AllSampler();

  /** Default constructor, trace/spans are logged, no trace/span dropped */
  public DDTracer() {
    this(UNASSIGNED_WRITER);
  }

  public DDTracer(Writer writer) {
    this(writer, new AllSampler());
  }

  public DDTracer(Writer writer, Sampler sampler) {
    this(UNASSIGNED_DEFAULT_SERVICE_NAME, writer, sampler);
  }

  public DDTracer(String defaultServiceName, Writer writer, Sampler sampler) {
    this.defaultServiceName = defaultServiceName;
    this.writer = writer;
    this.writer.start();
    this.sampler = sampler;
    registry = new CodecRegistry();
    registry.register(Format.Builtin.HTTP_HEADERS, new HTTPCodec());
  }

  /**
   * Returns the list of span context decorators
   *
   * @return the list of span context decorators
   */
  public List<DDSpanContextDecorator> getSpanContextDecorators(String tag) {
    return spanContextDecorators.get(tag);
  }

  /**
   * Add a new decorator in the list ({@link DDSpanContextDecorator})
   *
   * @param decorator The decorator in the list
   */
  public void addDecorator(DDSpanContextDecorator decorator) {

    List<DDSpanContextDecorator> list = spanContextDecorators.get(decorator.getMatchingTag());
    if (list == null) {
      list = new ArrayList<DDSpanContextDecorator>();
    }
    list.add(decorator);

    spanContextDecorators.put(decorator.getMatchingTag(), list);
  }

  public DDSpanBuilder buildSpan(String operationName) {
    return new DDSpanBuilder(operationName, this);
  }

  public <T> void inject(SpanContext spanContext, Format<T> format, T carrier) {

    Codec<T> codec = registry.get(format);
    if (codec == null) {
      logger.warn("Unsupported format for propagation - {}", format.getClass().getName());
    } else {
      codec.inject((DDSpanContext) spanContext, carrier);
    }
  }

  public <T> SpanContext extract(Format<T> format, T carrier) {

    Codec<T> codec = registry.get(format);
    if (codec == null) {
      logger.warn("Unsupported format for propagation - {}", format.getClass().getName());
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
  public void write(List<DDBaseSpan<?>> trace) {
    if (trace.isEmpty()) {
      return;
    }
    if (this.sampler.sample(trace.get(0))) {
      this.writer.write(trace);
    }
  }

  public void close() {
    writer.close();
  }

  /** Spans are built using this builder */
  public class DDSpanBuilder implements SpanBuilder {
    private final ActiveSpanSource spanSource;

    /** Each span must have an operationName according to the opentracing specification */
    private String operationName;

    // Builder attributes
    private Map<String, Object> tags = Collections.emptyMap();
    private long timestamp;
    private SpanContext parent;
    private String serviceName;
    private String resourceName;
    private boolean errorFlag;
    private String spanType;
    private boolean ignoreActiveSpan = false;

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
      ActiveSpan activeSpan = spanSource.makeActive(startSpan());
      logger.debug("{} - Starting a new active span.", activeSpan);
      return activeSpan;
    }

    @Override
    public DDSpan startManual() {
      DDSpan span = startSpan();
      logger.debug("{} - Starting a new manuel span.", span);
      return span;
    }

    @Override
    @Deprecated
    public DDSpan start() {
      return startManual();
    }

    @Override
    public DDSpanBuilder withTag(String tag, Number number) {
      return withTag(tag, (Object) number);
    }

    @Override
    public DDSpanBuilder withTag(String tag, String string) {
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
    public DDSpanBuilder withTag(String tag, boolean bool) {
      return withTag(tag, (Object) bool);
    }

    public DDSpanBuilder(String operationName, ActiveSpanSource spanSource) {
      this.operationName = operationName;
      this.spanSource = spanSource;
    }

    @Override
    public DDSpanBuilder withStartTimestamp(long timestampMillis) {
      this.timestamp = timestampMillis;
      return this;
    }

    public DDSpanBuilder withServiceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    public DDSpanBuilder withResourceName(String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    public DDSpanBuilder withErrorFlag() {
      this.errorFlag = true;
      return this;
    }

    public DDSpanBuilder withSpanType(String spanType) {
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
    public DDSpanBuilder asChildOf(BaseSpan<?> span) {
      return asChildOf(span == null ? null : span.context());
    }

    @Override
    public DDSpanBuilder asChildOf(SpanContext spanContext) {
      this.parent = spanContext;
      return this;
    }

    @Override
    public DDSpanBuilder addReference(String referenceType, SpanContext spanContext) {
      logger.debug("`addReference` method is not implemented. Doing nothing");
      return this;
    }

    // Private methods
    private DDSpanBuilder withTag(String tag, Object value) {
      if (this.tags.isEmpty()) {
        this.tags = new HashMap<String, Object>();
      }
      this.tags.put(tag, value);
      return this;
    }

    private long generateNewId() {
      return System.nanoTime();
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
      final List<DDBaseSpan<?>> parentTrace;

      DDSpanContext context;
      SpanContext parentContext = this.parent;
      if (parentContext == null && !ignoreActiveSpan) {
        // use the ActiveSpan as parent unless overridden or ignored.
        ActiveSpan activeSpan = activeSpan();
        if (activeSpan != null) parentContext = activeSpan.context();
      }

      if (parentContext instanceof DDSpanContext) {
        DDSpanContext ddsc = (DDSpanContext) parentContext;
        traceId = ddsc.getTraceId();
        parentSpanId = ddsc.getSpanId();
        baggage = ddsc.getBaggageItems();
        parentTrace = ddsc.getTrace();

        if (this.serviceName == null) this.serviceName = ddsc.getServiceName();
        if (this.spanType == null) this.spanType = ddsc.getSpanType();
      } else {
        traceId = spanId;
        parentSpanId = 0L;
        baggage = null;
        parentTrace = null;
      }

      if (serviceName == null) {
        serviceName = defaultServiceName;
      }

      String operationName = this.operationName != null ? this.operationName : this.resourceName;

      //this.operationName, this.tags,

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

      // Force the lang meta
      if (context.getBaggageItem(DDSpanContext.LANGUAGE_FIELDNAME) == null) {
        context.setBaggageItem(DDSpanContext.LANGUAGE_FIELDNAME, "java");
      }

      return context;
    }
  }

  private static class CodecRegistry {

    private final Map<Format<?>, Codec<?>> codecs = new HashMap<Format<?>, Codec<?>>();

    @SuppressWarnings("unchecked")
    <T> Codec<T> get(Format<T> format) {
      return (Codec<T>) codecs.get(format);
    }

    public <T> void register(Format<T> format, Codec<T> codec) {
      codecs.put(format, codec);
    }
  }

  @Override
  public String toString() {
    return "DDTracer{" + "writer=" + writer + ", sampler=" + sampler + '}';
  }
}
