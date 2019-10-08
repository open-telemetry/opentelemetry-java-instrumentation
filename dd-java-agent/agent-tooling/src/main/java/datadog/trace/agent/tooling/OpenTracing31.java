package datadog.trace.agent.tooling;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP;
import static java.util.Collections.singletonMap;

import datadog.trace.api.interceptor.MutableSpan;
import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.AgentTracer.TracerAPI;
import datadog.trace.instrumentation.api.Propagation;
import datadog.trace.instrumentation.api.Propagation.Getter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.noop.NoopSpan;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public final class OpenTracing31 implements TracerAPI {

  private final Tracer tracer = GlobalTracer.get();
  private final OT31Propagation propagation = new OT31Propagation();

  private final OT31Span NOOP_SPAN = new OT31Span("", NoopSpan.INSTANCE);

  @Override
  public AgentSpan startSpan(final String spanName) {
    return new OT31Span(spanName);
  }

  @Override
  public AgentSpan startSpan(final String spanName, final long startTimeMicros) {
    return new OT31Span(spanName, startTimeMicros);
  }

  @Override
  public AgentSpan startSpan(final String spanName, final AgentSpan.Context parent) {
    return new OT31Span(spanName, parent);
  }

  @Override
  public AgentSpan startSpan(
      final String spanName, final AgentSpan.Context parent, final long startTimeMicros) {
    return new OT31Span(spanName, parent, startTimeMicros);
  }

  @Override
  public AgentScope activateSpan(final AgentSpan span, final boolean finishSpanOnClose) {
    // when span is noopSpan(), the scope returned is not a TracerScope
    final Scope scope = tracer.scopeManager().activate(((OT31Span) span).span, finishSpanOnClose);
    return new OT31Scope(span, scope);
  }

  @Override
  public AgentSpan activeSpan() {
    final Span span = tracer.activeSpan();
    final String spanName;
    if (span instanceof MutableSpan) {
      spanName = ((MutableSpan) span).getOperationName();
    } else {
      spanName = "";
    }
    return span == null ? null : new OT31Span(spanName, span);
  }

  @Override
  public TraceScope activeScope() {
    final Scope scope = tracer.scopeManager().active();
    if (scope instanceof TraceScope) {
      return (TraceScope) scope;
    } else {
      return null;
    }
  }

  @Override
  public Propagation propagate() {
    return propagation;
  }

  @Override
  public AgentSpan noopSpan() {
    return NOOP_SPAN;
  }

  private final class OT31Span implements AgentSpan {

    private final Span span;
    private volatile String spanName;

    public OT31Span(final String spanName) {
      this(spanName, tracer.buildSpan(spanName).start());
    }

    public OT31Span(final String spanName, final long startTimeMicros) {
      this(spanName, tracer.buildSpan(spanName).withStartTimestamp(startTimeMicros).start());
    }

    public OT31Span(final String spanName, final Context parent) {
      this(
          spanName,
          tracer
              .buildSpan(spanName)
              .ignoreActiveSpan()
              .asChildOf(((OTContext) parent).context)
              .start());
    }

    public OT31Span(final String spanName, final Context parent, final long startTimeMicros) {
      this(
          spanName,
          tracer
              .buildSpan(spanName)
              .ignoreActiveSpan()
              .asChildOf(((OTContext) parent).context)
              .withStartTimestamp(startTimeMicros)
              .start());
    }

    public OT31Span(final String spanName, final Span span) {
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
      Tags.ERROR.set(span, error);
      return this;
    }

    @Override
    public AgentSpan setErrorMessage(final String errorMessage) {
      span.log(singletonMap(Fields.MESSAGE, errorMessage));
      return this;
    }

    @Override
    public AgentSpan addThrowable(final Throwable throwable) {
      span.log(singletonMap(ERROR_OBJECT, throwable));
      return this;
    }

    @Override
    public OTContext context() {
      final SpanContext context = span.context();
      return new OTContext(context);
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

    Span getSpan() {
      return span;
    }
  }

  private final class OT31Scope implements AgentScope {

    private final OT31Span span;
    private final Scope scope;

    public OT31Scope(final AgentSpan span, final Scope scope) {
      assert span instanceof OT31Span;
      this.span = (OT31Span) span;
      this.scope = scope;
    }

    @Override
    public void close() {
      scope.close();
    }

    @Override
    public void setAsyncPropagation(final boolean value) {
      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(value);
      }
    }

    @Override
    public AgentSpan span() {
      return span;
    }
  }

  private final class OT31Propagation implements Propagation {

    @Override
    public TraceScope.Continuation capture() {
      final Scope active = tracer.scopeManager().active();
      if (active instanceof TraceScope) {
        return ((TraceScope) active).capture();
      } else {
        return null;
      }
    }

    @Override
    public <C> void inject(final AgentSpan span, final C carrier, final Setter<C> setter) {
      assert span instanceof OT31Span;
      tracer.inject(
          ((OT31Span) span).getSpan().context(),
          TEXT_MAP,
          new OT31Propagation.Injector<>(carrier, setter));
    }

    private final class Injector<C> implements TextMap {
      private final C carrier;
      private final Setter<C> setter;

      public Injector(final C carrier, final Setter<C> setter) {
        this.carrier = carrier;
        this.setter = setter;
      }

      @Override
      public Iterator<Entry<String, String>> iterator() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void put(final String key, final String value) {
        setter.set(carrier, key, value);
      }
    }

    @Override
    public <C> AgentSpan.Context extract(final C carrier, final Getter<C> getter) {
      return new OTContext(tracer.extract(TEXT_MAP, new Extractor(carrier, getter)));
    }
  }

  private static final class Extractor<C> implements TextMap {
    private final Map<String, String> extracted;

    public Extractor(final C carrier, final Getter<C> getter) {
      extracted = new HashMap<>();
      for (final String key : getter.keys(carrier)) {
        extracted.put(key, getter.get(carrier, key));
      }
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
      return extracted.entrySet().iterator();
    }

    @Override
    public void put(final String key, final String value) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class OTContext implements AgentSpan.Context, SpanContext {
    private final SpanContext context;

    public OTContext(final SpanContext context) {
      this.context = context;
    }

    @Override
    public Iterable<Entry<String, String>> baggageItems() {
      return context.baggageItems();
    }
  }
}
