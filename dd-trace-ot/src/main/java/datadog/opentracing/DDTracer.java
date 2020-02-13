package datadog.opentracing;

import datadog.opentracing.decorators.AbstractDecorator;
import datadog.opentracing.decorators.DDDecoratorsFactory;
import datadog.opentracing.jfr.DDNoopScopeEventFactory;
import datadog.opentracing.jfr.DDScopeEventFactory;
import datadog.opentracing.propagation.ExtractedContext;
import datadog.opentracing.propagation.HttpCodec;
import datadog.opentracing.propagation.TagContext;
import datadog.opentracing.scopemanager.ContextualScopeManager;
import datadog.opentracing.scopemanager.ScopeContext;
import datadog.trace.api.Config;
import datadog.trace.api.interceptor.MutableSpan;
import datadog.trace.api.interceptor.TraceInterceptor;
import datadog.trace.api.sampling.PrioritySampling;
import datadog.trace.common.sampling.PrioritySampler;
import datadog.trace.common.sampling.Sampler;
import datadog.trace.common.writer.DDAgentWriter;
import datadog.trace.common.writer.Writer;
import datadog.trace.common.writer.ddagent.DDAgentResponseListener;
import datadog.trace.context.ScopeListener;
import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import io.opentracing.tag.Tag;
import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** DDTracer makes it easy to send traces and span to DD using the OpenTracing API. */
@Slf4j
public class DDTracer implements io.opentracing.Tracer, Closeable, datadog.trace.api.Tracer {
  // UINT64 max value
  public static final BigInteger TRACE_ID_MAX =
      BigInteger.valueOf(2).pow(64).subtract(BigInteger.ONE);
  public static final BigInteger TRACE_ID_MIN = BigInteger.ZERO;

  /** Default service name if none provided on the trace or span */
  final String serviceName;
  /** Writer is an charge of reporting traces and spans to the desired endpoint */
  final Writer writer;
  /** Sampler defines the sampling policy in order to reduce the number of traces for instance */
  final Sampler sampler;
  /** Scope manager is in charge of managing the scopes from which spans are created */
  final ScopeManager scopeManager;

  /** A set of tags that are added only to the application's root span */
  private final Map<String, String> localRootSpanTags;
  /** A set of tags that are added to every span */
  private final Map<String, String> defaultSpanTags;
  /** A configured mapping of service names to update with new values */
  private final Map<String, String> serviceNameMappings;

  /** number of spans in a pending trace before they get flushed */
  @Getter private final int partialFlushMinSpans;

  /**
   * JVM shutdown callback, keeping a reference to it to remove this if DDTracer gets destroyed
   * earlier
   */
  private final Thread shutdownCallback;

  /** Span context decorators */
  private final Map<String, List<AbstractDecorator>> spanContextDecorators =
      new ConcurrentHashMap<>();

  private final SortedSet<TraceInterceptor> interceptors =
      new ConcurrentSkipListSet<>(
          new Comparator<TraceInterceptor>() {
            @Override
            public int compare(final TraceInterceptor o1, final TraceInterceptor o2) {
              return Integer.compare(o1.priority(), o2.priority());
            }
          });

  private final HttpCodec.Injector injector;
  private final HttpCodec.Extractor extractor;

  public static class DDTracerBuilder {

    public DDTracerBuilder() {
      // Apply the default values from config.
      config(Config.get());
    }

    public DDTracerBuilder withProperties(final Properties properties) {
      return config(Config.get(properties));
    }

    public DDTracerBuilder config(final Config config) {
      this.config = config;
      serviceName(config.getServiceName());
      // Explicitly skip setting writer to avoid allocating resources prematurely.
      sampler(Sampler.Builder.forConfig(config));
      injector(HttpCodec.createInjector(config));
      extractor(HttpCodec.createExtractor(config, config.getHeaderTags()));
      scopeManager(
          new ContextualScopeManager(config.getScopeDepthLimit(), createScopeEventFactory()));
      localRootSpanTags(config.getLocalRootSpanTags());
      defaultSpanTags(config.getMergedSpanTags());
      serviceNameMappings(config.getServiceMapping());
      taggedHeaders(config.getHeaderTags());
      partialFlushMinSpans(config.getPartialFlushMinSpans());
      return this;
    }
  }

  /** By default, report to local agent and collect all traces. */
  @Deprecated
  public DDTracer() {
    this(Config.get());
  }

  @Deprecated
  public DDTracer(final String serviceName) {
    this(serviceName, Config.get());
  }

  @Deprecated
  public DDTracer(final Properties config) {
    this(Config.get(config));
  }

  @Deprecated
  public DDTracer(final Config config) {
    this(config.getServiceName(), config);
  }

  // This constructor is already used in the wild, so we have to keep it inside this API for now.
  @Deprecated
  public DDTracer(final String serviceName, final Writer writer, final Sampler sampler) {
    this(serviceName, writer, sampler, Config.get().getLocalRootSpanTags());
  }

  @Deprecated
  private DDTracer(final String serviceName, final Config config) {
    this(
        serviceName,
        Writer.Builder.forConfig(config),
        Sampler.Builder.forConfig(config),
        config.getLocalRootSpanTags(),
        config.getMergedSpanTags(),
        config.getServiceMapping(),
        config.getHeaderTags(),
        config.getPartialFlushMinSpans());
    log.debug("Using config: {}", config);
  }

  /** Visible for testing */
  @Deprecated
  DDTracer(
      final String serviceName,
      final Writer writer,
      final Sampler sampler,
      final Map<String, String> runtimeTags) {
    this(
        serviceName,
        writer,
        sampler,
        runtimeTags,
        Collections.<String, String>emptyMap(),
        Collections.<String, String>emptyMap(),
        Collections.<String, String>emptyMap(),
        0);
  }

  @Deprecated
  public DDTracer(final Writer writer) {
    this(Config.get(), writer);
  }

  @Deprecated
  public DDTracer(final Config config, final Writer writer) {
    this(
        config.getServiceName(),
        writer,
        Sampler.Builder.forConfig(config),
        config.getLocalRootSpanTags(),
        config.getMergedSpanTags(),
        config.getServiceMapping(),
        config.getHeaderTags(),
        config.getPartialFlushMinSpans());
  }

  @Deprecated
  public DDTracer(
      final String serviceName,
      final Writer writer,
      final Sampler sampler,
      final String runtimeId,
      final Map<String, String> localRootSpanTags,
      final Map<String, String> defaultSpanTags,
      final Map<String, String> serviceNameMappings,
      final Map<String, String> taggedHeaders) {
    this(
        serviceName,
        writer,
        sampler,
        customRuntimeTags(runtimeId, localRootSpanTags),
        defaultSpanTags,
        serviceNameMappings,
        taggedHeaders,
        Config.get().getPartialFlushMinSpans());
  }

  @Deprecated
  public DDTracer(
      final String serviceName,
      final Writer writer,
      final Sampler sampler,
      final Map<String, String> localRootSpanTags,
      final Map<String, String> defaultSpanTags,
      final Map<String, String> serviceNameMappings,
      final Map<String, String> taggedHeaders) {
    this(
        serviceName,
        writer,
        sampler,
        localRootSpanTags,
        defaultSpanTags,
        serviceNameMappings,
        taggedHeaders,
        Config.get().getPartialFlushMinSpans());
  }

  @Deprecated
  public DDTracer(
      final String serviceName,
      final Writer writer,
      final Sampler sampler,
      final Map<String, String> localRootSpanTags,
      final Map<String, String> defaultSpanTags,
      final Map<String, String> serviceNameMappings,
      final Map<String, String> taggedHeaders,
      final int partialFlushMinSpans) {
    this(
        Config.get(),
        serviceName,
        writer,
        sampler,
        HttpCodec.createInjector(Config.get()),
        HttpCodec.createExtractor(Config.get(), taggedHeaders),
        new ContextualScopeManager(Config.get().getScopeDepthLimit(), createScopeEventFactory()),
        localRootSpanTags,
        defaultSpanTags,
        serviceNameMappings,
        taggedHeaders,
        partialFlushMinSpans);
  }

  @Builder
  // These field names must be stable to ensure the builder api is stable.
  private DDTracer(
      final Config config,
      final String serviceName,
      final Writer writer,
      final Sampler sampler,
      final HttpCodec.Injector injector,
      final HttpCodec.Extractor extractor,
      final ScopeManager scopeManager,
      final Map<String, String> localRootSpanTags,
      final Map<String, String> defaultSpanTags,
      final Map<String, String> serviceNameMappings,
      final Map<String, String> taggedHeaders,
      final int partialFlushMinSpans) {

    assert localRootSpanTags != null;
    assert defaultSpanTags != null;
    assert serviceNameMappings != null;
    assert taggedHeaders != null;

    this.serviceName = serviceName;
    if (writer == null) {
      this.writer = Writer.Builder.forConfig(config);
    } else {
      this.writer = writer;
    }
    this.sampler = sampler;
    this.injector = injector;
    this.extractor = extractor;
    this.scopeManager = scopeManager;
    this.localRootSpanTags = localRootSpanTags;
    this.defaultSpanTags = defaultSpanTags;
    this.serviceNameMappings = serviceNameMappings;
    this.partialFlushMinSpans = partialFlushMinSpans;

    this.writer.start();

    shutdownCallback = new ShutdownHook(this);
    try {
      Runtime.getRuntime().addShutdownHook(shutdownCallback);
    } catch (final IllegalStateException ex) {
      // The JVM is already shutting down.
    }

    if (this.writer instanceof DDAgentWriter && sampler instanceof DDAgentResponseListener) {
      ((DDAgentWriter) this.writer).addResponseListener((DDAgentResponseListener) this.sampler);
    }

    log.info("New instance: {}", this);

    final List<AbstractDecorator> decorators = DDDecoratorsFactory.createBuiltinDecorators();
    for (final AbstractDecorator decorator : decorators) {
      addDecorator(decorator);
    }

    registerClassLoader(ClassLoader.getSystemClassLoader());

    // Ensure that PendingTrace.SPAN_CLEANER is initialized in this thread:
    // FIXME: add test to verify the span cleaner thread is started with this call.
    PendingTrace.initialize();
  }

  @Override
  public void finalize() {
    try {
      Runtime.getRuntime().removeShutdownHook(shutdownCallback);
      shutdownCallback.run();
    } catch (final Exception e) {
      log.error("Error while finalizing DDTracer.", e);
    }
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
    log.debug(
        "Decorator added: '{}' -> {}", decorator.getMatchingTag(), decorator.getClass().getName());
  }

  @Deprecated
  public void addScopeContext(final ScopeContext context) {
    if (scopeManager instanceof ContextualScopeManager) {
      ((ContextualScopeManager) scopeManager).addScopeContext(context);
    }
  }

  /**
   * If an application is using a non-system classloader, that classloader should be registered
   * here. Due to the way Spring Boot structures its' executable jar, this might log some warnings.
   *
   * @param classLoader to register.
   */
  public void registerClassLoader(final ClassLoader classLoader) {
    try {
      for (final TraceInterceptor interceptor :
          ServiceLoader.load(TraceInterceptor.class, classLoader)) {
        addTraceInterceptor(interceptor);
      }
    } catch (final ServiceConfigurationError e) {
      log.warn("Problem loading TraceInterceptor for classLoader: " + classLoader, e);
    }
  }

  @Override
  public ScopeManager scopeManager() {
    return scopeManager;
  }

  @Override
  public Span activeSpan() {
    return scopeManager.activeSpan();
  }

  @Override
  public Scope activateSpan(final Span span) {
    return scopeManager.activate(span);
  }

  @Override
  public SpanBuilder buildSpan(final String operationName) {
    return new DDSpanBuilder(operationName, scopeManager);
  }

  @Override
  public <T> void inject(final SpanContext spanContext, final Format<T> format, final T carrier) {
    if (carrier instanceof TextMapInject) {
      final DDSpanContext ddSpanContext = (DDSpanContext) spanContext;

      final DDSpan rootSpan = ddSpanContext.getTrace().getRootSpan();
      setSamplingPriorityIfNecessary(rootSpan);

      injector.inject(ddSpanContext, (TextMapInject) carrier);
    } else {
      log.debug("Unsupported format for propagation - {}", format.getClass().getName());
    }
  }

  @Override
  public <T> SpanContext extract(final Format<T> format, final T carrier) {
    if (carrier instanceof TextMapExtract) {
      return extractor.extract((TextMapExtract) carrier);
    } else {
      log.debug("Unsupported format for propagation - {}", format.getClass().getName());
      return null;
    }
  }

  /**
   * We use the sampler to know if the trace has to be reported/written. The sampler is called on
   * the first span (root span) of the trace. If the trace is marked as a sample, we report it.
   *
   * @param trace a list of the spans related to the same trace
   */
  void write(final Collection<DDSpan> trace) {
    if (trace.isEmpty()) {
      return;
    }
    final ArrayList<DDSpan> writtenTrace;
    if (interceptors.isEmpty()) {
      writtenTrace = new ArrayList<>(trace);
    } else {
      Collection<? extends MutableSpan> interceptedTrace = new ArrayList<>(trace);
      for (final TraceInterceptor interceptor : interceptors) {
        interceptedTrace = interceptor.onTraceComplete(interceptedTrace);
      }
      writtenTrace = new ArrayList<>(interceptedTrace.size());
      for (final MutableSpan span : interceptedTrace) {
        if (span instanceof DDSpan) {
          writtenTrace.add((DDSpan) span);
        }
      }
    }
    incrementTraceCount();

    if (!writtenTrace.isEmpty()) {
      final DDSpan rootSpan = (DDSpan) writtenTrace.get(0).getLocalRootSpan();
      setSamplingPriorityIfNecessary(rootSpan);

      final DDSpan spanToSample = rootSpan == null ? writtenTrace.get(0) : rootSpan;
      if (sampler.sample(spanToSample)) {
        writer.write(writtenTrace);
      }
    }
  }

  void setSamplingPriorityIfNecessary(final DDSpan rootSpan) {
    // There's a race where multiple threads can see PrioritySampling.UNSET here
    // This check skips potential complex sampling priority logic when we know its redundant
    // Locks inside DDSpanContext ensure the correct behavior in the race case

    if (sampler instanceof PrioritySampler
        && rootSpan != null
        && rootSpan.context().getSamplingPriority() == PrioritySampling.UNSET) {

      ((PrioritySampler) sampler).setSamplingPriority(rootSpan);
    }
  }

  /** Increment the reported trace count, but do not write a trace. */
  void incrementTraceCount() {
    writer.incrementTraceCount();
  }

  @Override
  public String getTraceId() {
    final Span activeSpan = activeSpan();
    if (activeSpan instanceof DDSpan) {
      return ((DDSpan) activeSpan).getTraceId().toString();
    }
    return "0";
  }

  @Override
  public String getSpanId() {
    final Span activeSpan = activeSpan();
    if (activeSpan instanceof DDSpan) {
      return ((DDSpan) activeSpan).getSpanId().toString();
    }
    return "0";
  }

  @Override
  public boolean addTraceInterceptor(final TraceInterceptor interceptor) {
    return interceptors.add(interceptor);
  }

  @Override
  public void addScopeListener(final ScopeListener listener) {
    if (scopeManager instanceof ContextualScopeManager) {
      ((ContextualScopeManager) scopeManager).addScopeListener(listener);
    }
  }

  @Override
  public void close() {
    PendingTrace.close();
    writer.close();
  }

  @Override
  public String toString() {
    return "DDTracer-"
        + Integer.toHexString(hashCode())
        + "{ serviceName="
        + serviceName
        + ", writer="
        + writer
        + ", sampler="
        + sampler
        + ", defaultSpanTags="
        + defaultSpanTags
        + '}';
  }

  @Deprecated
  private static Map<String, String> customRuntimeTags(
      final String runtimeId, final Map<String, String> applicationRootSpanTags) {
    final Map<String, String> runtimeTags = new HashMap<>(applicationRootSpanTags);
    runtimeTags.put(Config.RUNTIME_ID_TAG, runtimeId);
    return Collections.unmodifiableMap(runtimeTags);
  }

  private static DDScopeEventFactory createScopeEventFactory() {
    try {
      return (DDScopeEventFactory)
          Class.forName("datadog.opentracing.jfr.openjdk.ScopeEventFactory").newInstance();
    } catch (final ClassFormatError | ReflectiveOperationException | NoClassDefFoundError e) {
      log.debug("Cannot create Openjdk JFR scope event factory", e);
    }
    return new DDNoopScopeEventFactory();
  }

  /** Spans are built using this builder */
  public class DDSpanBuilder implements SpanBuilder {
    private final ScopeManager scopeManager;

    /** Each span must have an operationName according to the opentracing specification */
    private final String operationName;

    // Builder attributes
    private final Map<String, Object> tags = new LinkedHashMap<String, Object>(defaultSpanTags);
    private long timestampMicro;
    private SpanContext parent;
    private String serviceName;
    private String resourceName;
    private boolean errorFlag;
    private String spanType;
    private boolean ignoreScope = false;

    public DDSpanBuilder(final String operationName, final ScopeManager scopeManager) {
      this.operationName = operationName;
      this.scopeManager = scopeManager;
    }

    @Override
    public SpanBuilder ignoreActiveSpan() {
      ignoreScope = true;
      return this;
    }

    private Span startSpan() {
      return new DDSpan(timestampMicro, buildSpanContext());
    }

    @Override
    public Scope startActive(final boolean finishSpanOnClose) {
      final Span span = startSpan();
      final Scope scope = scopeManager.activate(span, finishSpanOnClose);
      log.debug("Starting a new active span: {}", span);
      return scope;
    }

    @Override
    @Deprecated
    public Span startManual() {
      return start();
    }

    @Override
    public Span start() {
      final Span span = startSpan();
      log.debug("Starting a new span: {}", span);
      return span;
    }

    @Override
    public DDSpanBuilder withTag(final String tag, final Number number) {
      return withTag(tag, (Object) number);
    }

    @Override
    public DDSpanBuilder withTag(final String tag, final String string) {
      return withTag(tag, (Object) string);
    }

    @Override
    public DDSpanBuilder withTag(final String tag, final boolean bool) {
      return withTag(tag, (Object) bool);
    }

    @Override
    public <T> SpanBuilder withTag(final Tag<T> tag, final T value) {
      return withTag(tag.getKey(), value);
    }

    @Override
    public DDSpanBuilder withStartTimestamp(final long timestampMicroseconds) {
      timestampMicro = timestampMicroseconds;
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
      errorFlag = true;
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
    public DDSpanBuilder asChildOf(final Span span) {
      return asChildOf(span == null ? null : span.context());
    }

    @Override
    public DDSpanBuilder asChildOf(final SpanContext spanContext) {
      parent = spanContext;
      return this;
    }

    @Override
    public DDSpanBuilder addReference(final String referenceType, final SpanContext spanContext) {
      if (spanContext == null) {
        return this;
      }
      if (!(spanContext instanceof ExtractedContext) && !(spanContext instanceof DDSpanContext)) {
        log.debug(
            "Expected to have a DDSpanContext or ExtractedContext but got "
                + spanContext.getClass().getName());
        return this;
      }
      if (References.CHILD_OF.equals(referenceType)
          || References.FOLLOWS_FROM.equals(referenceType)) {
        return asChildOf(spanContext);
      } else {
        log.debug("Only support reference type of CHILD_OF and FOLLOWS_FROM");
      }
      return this;
    }

    // Private methods
    private DDSpanBuilder withTag(final String tag, final Object value) {
      if (value == null || (value instanceof String && ((String) value).isEmpty())) {
        tags.remove(tag);
      } else {
        tags.put(tag, value);
      }
      return this;
    }

    private BigInteger generateNewId() {
      // It is **extremely** unlikely to generate the value "0" but we still need to handle that
      // case
      BigInteger value;
      do {
        value = new BigInteger(63, ThreadLocalRandom.current());
      } while (value.signum() == 0);

      return value;
    }

    /**
     * Build the SpanContext, if the actual span has a parent, the following attributes must be
     * propagated: - ServiceName - Baggage - Trace (a list of all spans related) - SpanType
     *
     * @return the context
     */
    private DDSpanContext buildSpanContext() {
      final BigInteger traceId;
      final BigInteger spanId = generateNewId();
      final BigInteger parentSpanId;
      final Map<String, String> baggage;
      final PendingTrace parentTrace;
      final int samplingPriority;
      final String origin;

      final DDSpanContext context;
      SpanContext parentContext = parent;
      if (parentContext == null && !ignoreScope) {
        // use the Scope as parent unless overridden or ignored.
        final Span activeSpan = scopeManager.activeSpan();
        if (activeSpan != null) {
          parentContext = activeSpan.context();
        }
      }

      // Propagate internal trace.
      // Note: if we are not in the context of distributed tracing and we are starting the first
      // root span, parentContext will be null at this point.
      if (parentContext instanceof DDSpanContext) {
        final DDSpanContext ddsc = (DDSpanContext) parentContext;
        traceId = ddsc.getTraceId();
        parentSpanId = ddsc.getSpanId();
        baggage = ddsc.getBaggageItems();
        parentTrace = ddsc.getTrace();
        samplingPriority = PrioritySampling.UNSET;
        origin = null;
        if (serviceName == null) {
          serviceName = ddsc.getServiceName();
        }

      } else {
        if (parentContext instanceof ExtractedContext) {
          // Propagate external trace
          final ExtractedContext extractedContext = (ExtractedContext) parentContext;
          traceId = extractedContext.getTraceId();
          parentSpanId = extractedContext.getSpanId();
          samplingPriority = extractedContext.getSamplingPriority();
          baggage = extractedContext.getBaggage();
        } else {
          // Start a new trace
          traceId = generateNewId();
          parentSpanId = BigInteger.ZERO;
          samplingPriority = PrioritySampling.UNSET;
          baggage = null;
        }

        // Get header tags and set origin whether propagating or not.
        if (parentContext instanceof TagContext) {
          tags.putAll(((TagContext) parentContext).getTags());
          origin = ((TagContext) parentContext).getOrigin();
        } else {
          origin = null;
        }

        tags.putAll(localRootSpanTags);

        parentTrace = new PendingTrace(DDTracer.this, traceId, serviceNameMappings);
      }

      if (serviceName == null) {
        serviceName = DDTracer.this.serviceName;
      }

      final String operationName = this.operationName != null ? this.operationName : resourceName;

      // some attributes are inherited from the parent
      context =
          new DDSpanContext(
              traceId,
              spanId,
              parentSpanId,
              serviceName,
              operationName,
              resourceName,
              samplingPriority,
              origin,
              baggage,
              errorFlag,
              spanType,
              tags,
              parentTrace,
              DDTracer.this);

      // Apply Decorators to handle any tags that may have been set via the builder.
      for (final Map.Entry<String, Object> tag : tags.entrySet()) {
        if (tag.getValue() == null) {
          context.setTag(tag.getKey(), null);
          continue;
        }

        boolean addTag = true;

        // Call decorators
        final List<AbstractDecorator> decorators = getSpanContextDecorators(tag.getKey());
        if (decorators != null) {
          for (final AbstractDecorator decorator : decorators) {
            try {
              addTag &= decorator.shouldSetTag(context, tag.getKey(), tag.getValue());
            } catch (final Throwable ex) {
              log.debug(
                  "Could not decorate the span decorator={}: {}",
                  decorator.getClass().getSimpleName(),
                  ex.getMessage());
            }
          }
        }

        if (!addTag) {
          context.setTag(tag.getKey(), null);
        }
      }

      return context;
    }
  }

  private static class ShutdownHook extends Thread {
    private final WeakReference<DDTracer> reference;

    private ShutdownHook(final DDTracer tracer) {
      super("dd-tracer-shutdown-hook");
      reference = new WeakReference<>(tracer);
    }

    @Override
    public void run() {
      final DDTracer tracer = reference.get();
      if (tracer != null) {
        tracer.close();
      }
    }
  }
}
