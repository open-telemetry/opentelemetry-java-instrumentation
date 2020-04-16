package datadog.trace.agent.tooling;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP_EXTRACT;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP_INJECT;
import static java.util.Collections.singletonMap;

import datadog.opentracing.DDSpan;
import datadog.trace.api.interceptor.MutableSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import datadog.trace.bootstrap.instrumentation.api.AgentPropagation.Getter;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentTracer.TracerAPI;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.noop.NoopSpan;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public final class OpenTracing32 implements TracerAPI {

  private final Tracer tracer = GlobalTracer.get();
  private final OT32AgentPropagation propagation = new OT32AgentPropagation();

  private final OT32Span NOOP_SPAN = new OT32Span("", NoopSpan.INSTANCE);

  @Override
  public AgentSpan startSpan(final String spanName) {
    return new OT32Span(spanName);
  }

  @Override
  public AgentSpan startSpan(final String spanName, final long startTimeMicros) {
    return new OT32Span(spanName, startTimeMicros);
  }

  @Override
  public AgentSpan startSpan(final String spanName, final AgentSpan.Context parent) {
    return new OT32Span(spanName, (OT32Context) parent);
  }

  @Override
  public AgentSpan startSpan(
      final String spanName, final AgentSpan.Context parent, final long startTimeMicros) {
    return new OT32Span(spanName, parent, startTimeMicros);
  }

  @Override
  public AgentScope activateSpan(final AgentSpan span, final boolean finishSpanOnClose) {
    // when span is noopSpan(), the scope returned is not a TracerScope
    final Scope scope = tracer.scopeManager().activate(((OT32Span) span).span, finishSpanOnClose);
    return new OT32Scope(span, scope);
  }

  @Override
  public AgentSpan activeSpan() {
    final Span span = tracer.activeSpan();
    if (span == null) {
      return null;
    }

    final String spanName;
    if (span instanceof MutableSpan) {
      spanName = ((MutableSpan) span).getOperationName();
    } else {
      spanName = "";
    }
    return new OT32Span(spanName, span);
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
  public AgentPropagation propagate() {
    return propagation;
  }

  @Override
  public AgentSpan noopSpan() {
    return NOOP_SPAN;
  }

  private final class OT32Span implements AgentSpan {

    private final Span span;
    private volatile String spanName;

    private OT32Span(final String spanName) {
      this(spanName, tracer.buildSpan(spanName).start());
    }

    private OT32Span(final String spanName, final long startTimeMicros) {
      this(spanName, tracer.buildSpan(spanName).withStartTimestamp(startTimeMicros).start());
    }

    private OT32Span(final String spanName, final OT32Context parent) {
      this(
          spanName,
          tracer.buildSpan(spanName).ignoreActiveSpan().asChildOf(parent.context).start());
    }

    private OT32Span(final String spanName, final Context parent, final long startTimeMicros) {
      this(
          spanName,
          tracer
              .buildSpan(spanName)
              .ignoreActiveSpan()
              .asChildOf(((OT32Context) parent).context)
              .withStartTimestamp(startTimeMicros)
              .start());
    }

    private OT32Span(final String spanName, final Span span) {
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
        Tags.ERROR.set(span, error);
      }
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
    public AgentSpan getLocalRootSpan() {
      if (span instanceof MutableSpan) {
        final MutableSpan root = ((MutableSpan) span).getLocalRootSpan();
        if (root == span) {
          return this;
        }
        return new OT32Span(root.getOperationName(), (Span) root);
      }
      return this;
    }

    @Override
    public boolean isSameTrace(final AgentSpan otherAgentSpan) {
      if (otherAgentSpan instanceof OT32Span) {
        final Span otherSpan = ((OT32Span) otherAgentSpan).span;
        if (span instanceof DDSpan && otherSpan instanceof DDSpan) {
          // minor optimization to avoid BigInteger.toString()
          return ((DDSpan) span).getTraceId().equals(((DDSpan) otherSpan).getTraceId());
        } else {
          return span.context().toTraceId().equals(otherSpan.context().toTraceId());
        }
      }
      return false;
    }

    @Override
    public OT32Context context() {
      final SpanContext context = span.context();
      return new OT32Context(context);
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

    @Override
    public boolean hasResourceName() {
      if (span instanceof DDSpan) {
        return ((DDSpan) span).context().hasResourceName();
      }
      return false;
    }

    private Span getSpan() {
      return span;
    }
  }

  private final class OT32Scope implements AgentScope {

    private final OT32Span span;
    private final Scope scope;

    private OT32Scope(final AgentSpan span, final Scope scope) {
      assert span instanceof OT32Span;
      this.span = (OT32Span) span;
      this.scope = scope;
    }

    @Override
    public void close() {
      scope.close();
    }

    @Override
    public AgentScope setAsyncPropagation(final boolean value) {
      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(value);
      }
      return this;
    }

    @Override
    public AgentSpan span() {
      return span;
    }
  }

  private final class OT32AgentPropagation implements AgentPropagation {

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
      assert span instanceof OT32Span;
      tracer.inject(
          ((OT32Span) span).getSpan().context(),
          TEXT_MAP_INJECT,
          new OT32AgentPropagation.Injector<>(carrier, setter));
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
      return new OT32Context(tracer.extract(TEXT_MAP_EXTRACT, new Extractor(carrier, getter)));
    }
  }

  private static final class Extractor<C> implements TextMapExtract {
    private final Map<String, String> extracted;

    private Extractor(final C carrier, final Getter<C> getter) {
      extracted = new HashMap<>();
      for (final String key : getter.keys(carrier)) {
        // extracted header value
        String value = getter.get(carrier, key);
        // in case of multiple values in the header, need to parse
        if (value != null) {
          final String[] split = value.split(",");
          if (split.length > 0) {
            value = split[0].trim();
          }
        }
        extracted.put(key, value);
      }
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
      return extracted.entrySet().iterator();
    }
  }

  private static final class OT32Context implements AgentSpan.Context, SpanContext {
    private final SpanContext context;

    private OT32Context(final SpanContext context) {
      this.context = context;
    }

    @Override
    public String toTraceId() {
      return context.toTraceId();
    }

    @Override
    public String toSpanId() {
      return context.toSpanId();
    }

    @Override
    public Iterable<Entry<String, String>> baggageItems() {
      return context.baggageItems();
    }
  }
}
