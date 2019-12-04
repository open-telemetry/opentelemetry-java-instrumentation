package datadog.opentracing;

import datadog.opentracing.decorators.AbstractDecorator;
import datadog.opentracing.decorators.DDDecoratorsFactory;
import datadog.opentracing.propagation.ExtractedContext;
import datadog.opentracing.propagation.HttpCodec;
import datadog.opentracing.scopemanager.ContextualScopeManager;
import datadog.opentracing.scopemanager.ScopeContext;
import datadog.trace.api.Config;
import datadog.trace.common.writer.Writer;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** DDTracer makes it easy to send traces and span to DD using the OpenTracing API. */
@Slf4j
public class DDTracer implements io.opentracing.Tracer, Closeable, datadog.trace.api.Tracer {
  // UINT64 max value
  public static final BigInteger TRACE_ID_MAX =
      BigInteger.valueOf(2).pow(64).subtract(BigInteger.ONE);
  public static final BigInteger TRACE_ID_MIN = BigInteger.ZERO;

  /** Writer is an charge of reporting traces and spans to the desired endpoint */
  final Writer writer;
  /** Scope manager is in charge of managing the scopes from which spans are created */
  final ContextualScopeManager scopeManager = new ContextualScopeManager();

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

  private final HttpCodec.Injector injector;
  private final HttpCodec.Extractor extractor;

  /** By default, report to local agent and collect all traces. */
  public DDTracer() {
    this(Config.get());
  }

  public DDTracer(final Properties config) {
    this(Config.get(config));
  }

  private DDTracer(final Config config) {
    this(Writer.Builder.forConfig(config), config.getPartialFlushMinSpans());
    log.debug("Using config: {}", config);
  }

  public DDTracer(final Config config, final Writer writer) {
    this(writer, config.getPartialFlushMinSpans());
  }

  public DDTracer(final Writer writer) {
    this(writer, Config.get().getPartialFlushMinSpans());
  }

  public DDTracer(final Writer writer, final int partialFlushMinSpans) {

    this.writer = writer;
    this.writer.start();
    this.partialFlushMinSpans = partialFlushMinSpans;

    shutdownCallback = new ShutdownHook(this);
    try {
      Runtime.getRuntime().addShutdownHook(shutdownCallback);
    } catch (final IllegalStateException ex) {
      // The JVM is already shutting down.
    }

    // TODO: we have too many constructors, we should move to some sort of builder approach
    injector = HttpCodec.createInjector();
    extractor = HttpCodec.createExtractor();

    log.info("New instance: {}", this);

    final List<AbstractDecorator> decorators = DDDecoratorsFactory.createBuiltinDecorators();
    for (final AbstractDecorator decorator : decorators) {
      addDecorator(decorator);
    }

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

  @Override
  public ContextualScopeManager scopeManager() {
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
  public DDSpanBuilder buildSpan(final String operationName) {
    return new DDSpanBuilder(operationName, scopeManager);
  }

  @Override
  public <T> void inject(final SpanContext spanContext, final Format<T> format, final T carrier) {
    if (carrier instanceof TextMapInject) {
      injector.inject((DDSpanContext) spanContext, (TextMapInject) carrier);
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

  /** @param trace a list of the spans related to the same trace */
  void write(final Collection<DDSpan> trace) {
    if (trace.isEmpty()) {
      return;
    }
    final ArrayList<DDSpan> writtenTrace = new ArrayList<>(trace);
    incrementTraceCount();
    // TODO: current trace implementation doesn't guarantee that first span is the root span
    // We may want to reconsider way this check is done.
    if (!writtenTrace.isEmpty()) {
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
    return "DDTracer-" + Integer.toHexString(hashCode()) + "{ writer=" + writer + '}';
  }

  /** Spans are built using this builder */
  public class DDSpanBuilder implements SpanBuilder {
    private final ScopeManager scopeManager;

    /** Each span must have an operationName according to the opentracing specification */
    private final String operationName;

    // Builder attributes
    private final Map<String, Object> tags = new HashMap<>();
    private long timestampMicro;
    private SpanContext parent;
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
      return new DDSpan(timestampMicro, buildSpanContext());
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
    public <T> SpanBuilder withTag(final Tag<T> tag, final T value) {
      return withTag(tag.getKey(), value);
    }

    @Override
    public DDSpanBuilder withStartTimestamp(final long timestampMicroseconds) {
      timestampMicro = timestampMicroseconds;
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
     * propagated: - Trace (a list of all spans related) - SpanType
     *
     * @return the context
     */
    private DDSpanContext buildSpanContext() {
      final BigInteger traceId;
      final BigInteger spanId = generateNewId();
      final BigInteger parentSpanId;
      final PendingTrace parentTrace;

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
        parentTrace = ddsc.getTrace();

      } else {
        if (parentContext instanceof ExtractedContext) {
          // Propagate external trace
          final ExtractedContext extractedContext = (ExtractedContext) parentContext;
          traceId = extractedContext.getTraceId();
          parentSpanId = extractedContext.getSpanId();
        } else {
          // Start a new trace
          traceId = generateNewId();
          parentSpanId = BigInteger.ZERO;
        }

        parentTrace = new PendingTrace(DDTracer.this, traceId);
      }

      // some attributes are inherited from the parent
      context =
          new DDSpanContext(
              traceId,
              spanId,
              parentSpanId,
              operationName,
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
