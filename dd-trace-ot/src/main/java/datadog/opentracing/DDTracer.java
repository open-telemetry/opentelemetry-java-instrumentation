package datadog.opentracing;

import datadog.opentracing.decorators.AbstractDecorator;
import datadog.opentracing.decorators.DDDecoratorsFactory;
import datadog.opentracing.propagation.ExtractedContext;
import datadog.opentracing.propagation.HttpCodec;
import datadog.opentracing.propagation.TagContext;
import datadog.opentracing.scopemanager.ContextualScopeManager;
import datadog.opentracing.scopemanager.ScopeContext;
import datadog.trace.api.Config;
import datadog.trace.api.interceptor.MutableSpan;
import datadog.trace.api.interceptor.TraceInterceptor;
import datadog.trace.api.sampling.PrioritySampling;
import datadog.trace.common.sampling.RateByServiceSampler;
import datadog.trace.common.sampling.Sampler;
import datadog.trace.common.writer.DDAgentWriter;
import datadog.trace.common.writer.DDApi;
import datadog.trace.common.writer.Writer;
import datadog.trace.context.ScopeListener;
import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** DDTracer makes it easy to send traces and span to DD using the OpenTracing API. */
@Slf4j
public class DDTracer implements io.opentracing.Tracer, Closeable, datadog.trace.api.Tracer {

  /** Default service name if none provided on the trace or span */
  final String serviceName;
  /** Writer is an charge of reporting traces and spans to the desired endpoint */
  final Writer writer;
  /** Sampler defines the sampling policy in order to reduce the number of traces for instance */
  final Sampler sampler;
  /** Scope manager is in charge of managing the scopes from which spans are created */
  final ContextualScopeManager scopeManager = new ContextualScopeManager();

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

  /** By default, report to local agent and collect all traces. */
  public DDTracer() {
    this(Config.get());
  }

  public DDTracer(final String serviceName) {
    this(serviceName, Config.get());
  }

  public DDTracer(final Properties config) {
    this(Config.get(config));
  }

  public DDTracer(final Config config) {
    this(config.getServiceName(), config);
  }

  // This constructor is already used in the wild, so we have to keep it inside this API for now.
  public DDTracer(final String serviceName, final Writer writer, final Sampler sampler) {
    this(serviceName, writer, sampler, Config.get().getLocalRootSpanTags());
  }

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

  public DDTracer(final Writer writer) {
    this(Config.get(), writer);
  }

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

  /**
   * @deprecated Use {@link #DDTracer(String, Writer, Sampler, Map, Map, Map, Map, int)} instead.
   */
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

  /**
   * @deprecated Use {@link #DDTracer(String, Writer, Sampler, Map, Map, Map, Map, int)} instead.
   */
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

  public DDTracer(
      final String serviceName,
      final Writer writer,
      final Sampler sampler,
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
    this.writer = writer;
    this.writer.start();
    this.sampler = sampler;
    this.localRootSpanTags = localRootSpanTags;
    this.defaultSpanTags = defaultSpanTags;
    this.serviceNameMappings = serviceNameMappings;
    this.partialFlushMinSpans = partialFlushMinSpans;

    shutdownCallback = new ShutdownHook(this);
    try {
      Runtime.getRuntime().addShutdownHook(shutdownCallback);
    } catch (final IllegalStateException ex) {
      // The JVM is already shutting down.
    }

    // TODO: we have too many constructors, we should move to some sort of builder approach
    injector = HttpCodec.createInjector(Config.get());
    extractor = HttpCodec.createExtractor(Config.get(), taggedHeaders);

    if (this.writer instanceof DDAgentWriter) {
      final DDApi api = ((DDAgentWriter) this.writer).getApi();
      if (sampler instanceof DDApi.ResponseListener) {
        api.addResponseListener((DDApi.ResponseListener) this.sampler);
      }
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
    scopeManager.addScopeContext(context);
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
  public ContextualScopeManager scopeManager() {
    return scopeManager;
  }

  @Override
  public Span activeSpan() {
    final Scope active = scopeManager.active();
    return active == null ? null : active.span();
  }

  @Override
  public DDSpanBuilder buildSpan(final String operationName) {
    return new DDSpanBuilder(operationName, scopeManager);
  }

  @Override
  public <T> void inject(final SpanContext spanContext, final Format<T> format, final T carrier) {
    if (carrier instanceof TextMap) {
      injector.inject((DDSpanContext) spanContext, (TextMap) carrier);
    } else {
      log.debug("Unsupported format for propagation - {}", format.getClass().getName());
    }
  }

  @Override
  public <T> SpanContext extract(final Format<T> format, final T carrier) {
    if (carrier instanceof TextMap) {
      return extractor.extract((TextMap) carrier);
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
    // TODO: current trace implementation doesn't guarantee that first span is the root span
    // We may want to reconsider way this check is done.
    if (!writtenTrace.isEmpty() && sampler.sample(writtenTrace.get(0))) {
      writer.write(writtenTrace);
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
      return ((DDSpan) activeSpan).getTraceId();
    }
    return "0";
  }

  @Override
  public String getSpanId() {
    final Span activeSpan = activeSpan();
    if (activeSpan instanceof DDSpan) {
      return ((DDSpan) activeSpan).getSpanId();
    }
    return "0";
  }

  @Override
  public boolean addTraceInterceptor(final TraceInterceptor interceptor) {
    return interceptors.add(interceptor);
  }

  @Override
  public void addScopeListener(final ScopeListener listener) {
    scopeManager.addScopeListener(listener);
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
      final String runtimeId, Map<String, String> applicationRootSpanTags) {
    final Map<String, String> runtimeTags = new HashMap<>(applicationRootSpanTags);
    runtimeTags.put(Config.RUNTIME_ID_TAG, runtimeId);
    return Collections.unmodifiableMap(runtimeTags);
  }

  /** Spans are built using this builder */
  public class DDSpanBuilder implements SpanBuilder {
    private final ScopeManager scopeManager;

    /** Each span must have an operationName according to the opentracing specification */
    private final String operationName;

    // Builder attributes
    private final Map<String, Object> tags = new HashMap<String, Object>(defaultSpanTags);
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

    private DDSpan startSpan() {
      final DDSpan span = new DDSpan(timestampMicro, buildSpanContext());
      if (sampler instanceof RateByServiceSampler) {
        ((RateByServiceSampler) sampler).initializeSamplingPriority(span);
      }
      return span;
    }

    @Override
    public Scope startActive(final boolean finishSpanOnClose) {
      final DDSpan span = startSpan();
      final Scope scope = scopeManager.activate(span, finishSpanOnClose);
      log.debug("Starting a new active span: {}", span);
      return scope;
    }

    @Override
    @Deprecated
    public DDSpan startManual() {
      return start();
    }

    @Override
    public DDSpan start() {
      final DDSpan span = startSpan();
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

    private String generateNewId() {
      // TODO: expand the range of numbers generated to be from 1 to uint 64 MAX
      // Ensure the generated ID is in a valid range:
      return String.valueOf(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    }

    /**
     * Build the SpanContext, if the actual span has a parent, the following attributes must be
     * propagated: - ServiceName - Baggage - Trace (a list of all spans related) - SpanType
     *
     * @return the context
     */
    private DDSpanContext buildSpanContext() {
      final String traceId;
      final String spanId = generateNewId();
      final String parentSpanId;
      final Map<String, String> baggage;
      final PendingTrace parentTrace;
      final int samplingPriority;
      final String origin;

      final DDSpanContext context;
      SpanContext parentContext = parent;
      if (parentContext == null && !ignoreScope) {
        // use the Scope as parent unless overridden or ignored.
        final Scope scope = scopeManager.active();
        if (scope != null) {
          parentContext = scope.span().context();
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
          parentSpanId = "0";
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
