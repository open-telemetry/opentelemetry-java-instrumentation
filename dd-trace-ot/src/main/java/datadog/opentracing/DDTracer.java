package datadog.opentracing;

import datadog.opentracing.propagation.ExtractedContext;
import datadog.opentracing.propagation.HttpCodec;
import datadog.opentracing.propagation.TextMapExtract;
import datadog.opentracing.propagation.TextMapInject;
import datadog.opentracing.scopemanager.ContextualScopeManager;
import datadog.opentracing.scopemanager.DDScope;
import datadog.trace.api.Config;
import datadog.trace.common.writer.Writer;
import datadog.trace.context.ScopeListener;
import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** DDTracer makes it easy to send traces and span to DD using the OpenTracing API. */
@Slf4j
public class DDTracer implements Closeable, datadog.trace.api.Tracer {
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

  private final HttpCodec.Injector injector;
  private final HttpCodec.Extractor extractor;

  /** By default, report to local agent and collect all traces. */
  public DDTracer() {
    this(Config.get());
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

  public ContextualScopeManager scopeManager() {
    return scopeManager;
  }

  public Span activeSpan() {
    return scopeManager.activeSpan();
  }

  public DDSpanBuilder buildSpan(final String operationName) {
    return new DDSpanBuilder(operationName, scopeManager);
  }

  public void inject(final SpanContext spanContext, final TextMapInject carrier) {
    injector.inject((DDSpanContext) spanContext, carrier);
  }

  public SpanContext extract(final TextMapExtract carrier) {
    return extractor.extract(carrier);
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
  public class DDSpanBuilder {
    private final ContextualScopeManager scopeManager;

    /** Each span must have an operationName according to the opentracing specification */
    private final String operationName;

    // Builder attributes
    private final Map<String, Object> tags = new LinkedHashMap<>();
    private long timestampMicro;
    private SpanContext parent;
    private boolean errorFlag;
    private boolean ignoreScope = false;

    public DDSpanBuilder(final String operationName, final ContextualScopeManager scopeManager) {
      this.operationName = operationName;
      this.scopeManager = scopeManager;
    }

    public DDSpanBuilder ignoreActiveSpan() {
      ignoreScope = true;
      return this;
    }

    private DDSpan startSpan() {
      return new DDSpan(timestampMicro, buildSpanContext());
    }

    public DDScope startActive(final boolean finishSpanOnClose) {
      final DDSpan span = startSpan();
      final DDScope scope = scopeManager.activate(span, finishSpanOnClose);
      log.debug("Starting a new active span: {}", span);
      return scope;
    }

    @Deprecated
    public DDSpan startManual() {
      return start();
    }

    public DDSpan start() {
      final DDSpan span = startSpan();
      log.debug("Starting a new span: {}", span);
      return span;
    }

    public DDSpanBuilder withTag(final String tag, final Number number) {
      return withTag(tag, (Object) number);
    }

    public DDSpanBuilder withTag(final String tag, final String string) {
      return withTag(tag, (Object) string);
    }

    public DDSpanBuilder withStartTimestamp(final long timestampMicroseconds) {
      timestampMicro = timestampMicroseconds;
      return this;
    }

    public DDSpanBuilder withErrorFlag() {
      errorFlag = true;
      return this;
    }

    public DDSpanBuilder asChildOf(final Span span) {
      return asChildOf(span == null ? null : span.context());
    }

    public DDSpanBuilder asChildOf(final SpanContext spanContext) {
      parent = spanContext;
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
     * propagated: - Trace (a list of all spans related)
     *
     * @return the context
     */
    private DDSpanContext buildSpanContext() {
      final BigInteger traceId;
      final BigInteger spanId = generateNewId();
      final BigInteger parentSpanId;
      final PendingTrace parentTrace;

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
      return new DDSpanContext(
          traceId,
          spanId,
          parentSpanId,
          operationName,
          errorFlag,
          tags,
          parentTrace,
          DDTracer.this);
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
