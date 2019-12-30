package datadog.trace.agent.tooling;

import datadog.opentracing.DDSpan;
import datadog.opentracing.DDTracer;
import datadog.opentracing.NoopSpan;
import datadog.opentracing.Span;
import datadog.opentracing.SpanContext;
import datadog.opentracing.propagation.TextMapExtract;
import datadog.opentracing.propagation.TextMapInject;
import datadog.opentracing.scopemanager.ContinuableScope;
import datadog.opentracing.scopemanager.DDScope;
import datadog.trace.instrumentation.api.AgentPropagation;
import datadog.trace.instrumentation.api.AgentPropagation.Getter;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.AgentTracer.TracerAPI;
import datadog.trace.instrumentation.api.TraceScope;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public final class AgentTracerImpl implements TracerAPI {

  private final DDTracer tracer;
  private final AgentPropagationImpl propagation = new AgentPropagationImpl();

  private final AgentSpanImpl NOOP_SPAN = new AgentSpanImpl("", NoopSpan.INSTANCE);

  public AgentTracerImpl(final DDTracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public AgentSpan startSpan(final String spanName) {
    return new AgentSpanImpl(spanName);
  }

  @Override
  public AgentSpan startSpan(final String spanName, final long startTimeMicros) {
    return new AgentSpanImpl(spanName, startTimeMicros);
  }

  @Override
  public AgentSpan startSpan(final String spanName, final AgentSpan.Context parent) {
    return new AgentSpanImpl(spanName, (AgentContextImpl) parent);
  }

  @Override
  public AgentSpan startSpan(
      final String spanName, final AgentSpan.Context parent, final long startTimeMicros) {
    return new AgentSpanImpl(spanName, parent, startTimeMicros);
  }

  @Override
  public AgentScope activateSpan(final AgentSpan span, final boolean finishSpanOnClose) {
    // when span is noopSpan(), the scope returned is not a TracerScope
    final DDScope scope =
        tracer.scopeManager().activate(((AgentSpanImpl) span).span, finishSpanOnClose);
    return new AgentScopeImpl(span, scope);
  }

  @Override
  public AgentSpan activeSpan() {
    final Span span = tracer.activeSpan();
    if (span == null) {
      return null;
    }

    final String spanName;
    if (span instanceof DDSpan) {
      spanName = ((DDSpan) span).getOperationName();
    } else {
      spanName = "";
    }
    return new AgentSpanImpl(spanName, span);
  }

  @Override
  public TraceScope activeScope() {
    final DDScope scope = tracer.scopeManager().active();
    if (scope instanceof ContinuableScope) {
      return new TraceScopeImpl((ContinuableScope) scope);
    } else {
      return null;
    }
  }

  @Override
  public AgentPropagation propagate() {
    return propagation;
  }

  @Override
  public AgentSpan noopSpan() {
    return NOOP_SPAN;
  }

  private final class AgentSpanImpl implements AgentSpan {

    private final Span span;
    private volatile String spanName;

    private AgentSpanImpl(final String spanName) {
      this(spanName, tracer.buildSpan(spanName).start());
    }

    private AgentSpanImpl(final String spanName, final long startTimeMicros) {
      this(spanName, tracer.buildSpan(spanName).withStartTimestamp(startTimeMicros).start());
    }

    private AgentSpanImpl(final String spanName, final AgentContextImpl parent) {
      this(
          spanName,
          tracer.buildSpan(spanName).ignoreActiveSpan().asChildOf(parent.context).start());
    }

    private AgentSpanImpl(final String spanName, final Context parent, final long startTimeMicros) {
      this(
          spanName,
          tracer
              .buildSpan(spanName)
              .ignoreActiveSpan()
              .asChildOf(((AgentContextImpl) parent).context)
              .withStartTimestamp(startTimeMicros)
              .start());
    }

    private AgentSpanImpl(final String spanName, final Span span) {
      this.spanName = spanName;
      this.span = span;
    }

    @Override
    public AgentSpan setTag(final String key, final boolean value) {
      span.setTag(key, value);
      return this;
    }

    @Override
    public AgentSpan setTag(final String key, final int value) {
      span.setTag(key, value);
      return this;
    }

    @Override
    public AgentSpan setTag(final String key, final long value) {
      span.setTag(key, value);
      return this;
    }

    @Override
    public AgentSpan setTag(final String key, final double value) {
      span.setTag(key, value);
      return this;
    }

    @Override
    public AgentSpan setTag(final String key, final String value) {
      span.setTag(key, value);
      return this;
    }

    @Override
    public AgentSpan setError(final boolean error) {
      if (span instanceof DDSpan) {
        ((DDSpan) span).setError(error);
      } else {
        span.setTag("error", error);
      }
      return this;
    }

    @Override
    public AgentSpan addThrowable(final Throwable throwable) {
      if (span instanceof DDSpan) {
        ((DDSpan) span).setErrorMeta(throwable);
      }
      return this;
    }

    @Override
    public AgentSpan getLocalRootSpan() {
      if (span instanceof DDSpan) {
        final DDSpan root = ((DDSpan) span).getLocalRootSpan();
        if (root == span) {
          return this;
        }
        return new AgentSpanImpl(root.getOperationName(), (Span) root);
      }
      return this;
    }

    @Override
    public AgentContextImpl context() {
      final SpanContext context = span.context();
      return new AgentContextImpl(context);
    }

    @Override
    public void finish() {
      span.finish();
    }

    @Override
    public String getSpanName() {
      return spanName;
    }

    @Override
    public void setSpanName(final String spanName) {
      this.spanName = spanName;
      span.setOperationName(spanName);
    }

    private Span getSpan() {
      return span;
    }
  }

  private final class AgentScopeImpl implements AgentScope {

    private final AgentSpanImpl span;
    private final DDScope scope;

    private AgentScopeImpl(final AgentSpan span, final DDScope scope) {
      assert span instanceof AgentSpanImpl;
      this.span = (AgentSpanImpl) span;
      this.scope = scope;
    }

    @Override
    public void close() {
      scope.close();
    }

    @Override
    public AgentSpan span() {
      return span;
    }
  }

  private static class TraceScopeImpl implements TraceScope {

    private final ContinuableScope scope;

    private TraceScopeImpl(final ContinuableScope scope) {
      this.scope = scope;
    }

    @Override
    public Continuation capture() {
      return new ContinuationImpl(scope.capture());
    }

    @Override
    public void close() {
      scope.close();
    }

    @Override
    public int hashCode() {
      return scope.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
      if (!(obj instanceof TraceScopeImpl)) {
        return false;
      }
      final TraceScopeImpl other = (TraceScopeImpl) obj;
      return scope.equals(other.scope);
    }
  }

  private static class ContinuationImpl implements TraceScope.Continuation {

    private final ContinuableScope.Continuation continuation;

    private ContinuationImpl(final ContinuableScope.Continuation continuation) {
      this.continuation = continuation;
    }

    @Override
    public TraceScope activate() {
      return new TraceScopeImpl(continuation.activate());
    }
  }

  private final class AgentPropagationImpl implements AgentPropagation {

    @Override
    public TraceScope.Continuation capture() {
      final DDScope active = tracer.scopeManager().active();
      if (active instanceof ContinuableScope) {
        return new ContinuationImpl(((ContinuableScope) active).capture());
      } else {
        return null;
      }
    }

    @Override
    public <C> void inject(final AgentSpan span, final C carrier, final Setter<C> setter) {
      assert span instanceof AgentSpanImpl;
      tracer.inject(
          ((AgentSpanImpl) span).getSpan().context(),
          new AgentPropagationImpl.Injector<>(carrier, setter));
    }

    private final class Injector<C> implements TextMapInject {
      private final C carrier;
      private final Setter<C> setter;

      private Injector(final C carrier, final Setter<C> setter) {
        this.carrier = carrier;
        this.setter = setter;
      }

      @Override
      public void put(final String key, final String value) {
        setter.set(carrier, key, value);
      }
    }

    @Override
    public <C> AgentSpan.Context extract(final C carrier, final Getter<C> getter) {
      return new AgentContextImpl(tracer.extract(new Extractor(carrier, getter)));
    }
  }

  private static final class Extractor<C> implements TextMapExtract {
    private final Map<String, String> extracted;

    private Extractor(final C carrier, final Getter<C> getter) {
      extracted = new HashMap<>();
      for (final String key : getter.keys(carrier)) {
        // extracted header value
        String s = getter.get(carrier, key);
        // in case of multiple values in the header, need to parse
        if (s != null) {
          s = s.split(",")[0].trim();
        }
        extracted.put(key, s);
      }
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
      return extracted.entrySet().iterator();
    }
  }

  private static final class AgentContextImpl implements AgentSpan.Context, SpanContext {
    private final SpanContext context;

    private AgentContextImpl(final SpanContext context) {
      this.context = context;
    }
  }
}
