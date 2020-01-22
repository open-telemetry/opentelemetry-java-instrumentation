package io.opentelemetry.auto.tooling;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.AgentPropagation;
import io.opentelemetry.auto.instrumentation.api.AgentPropagation.Getter;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.AgentTracer.TracerAPI;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class AgentTracerImpl implements TracerAPI {

  private final Tracer tracer;
  private final AgentPropagationImpl propagation = new AgentPropagationImpl();

  private final AgentSpanImpl noopSpan;

  public AgentTracerImpl(final Tracer tracer) {
    this.tracer = tracer;
    noopSpan = new AgentSpanImpl(DefaultSpan.getInvalid());
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
    final Scope scope = tracer.withSpan(((AgentSpanImpl) span).span);
    return new AgentScopeImpl(span, scope, finishSpanOnClose);
  }

  @Override
  public AgentSpan activeSpan() {
    final Span span = tracer.getCurrentSpan();
    if (!span.getContext().isValid()) {
      return null;
    }
    return new AgentSpanImpl(span);
  }

  @Override
  public AgentPropagation propagate() {
    return propagation;
  }

  @Override
  public AgentSpan noopSpan() {
    return noopSpan;
  }

  private final class AgentSpanImpl implements AgentSpan {

    private final Span span;

    private AgentSpanImpl(final String spanName) {
      this(tracer.spanBuilder(spanName).startSpan());
    }

    private AgentSpanImpl(final String spanName, final long startTimeMicros) {
      this(
          tracer
              .spanBuilder(spanName)
              .setStartTimestamp(MICROSECONDS.toNanos(startTimeMicros))
              .startSpan());
    }

    private AgentSpanImpl(final String spanName, final AgentContextImpl parent) {
      final SpanContext context = parent.context;
      final Span.Builder spanBuilder = tracer.spanBuilder(spanName);
      if (context == null) {
        spanBuilder.setNoParent();
      } else {
        spanBuilder.setParent(context);
      }
      span = spanBuilder.startSpan();
    }

    private AgentSpanImpl(final String spanName, final Context parent, final long startTimeMicros) {
      final SpanContext context = ((AgentContextImpl) parent).context;
      final Span.Builder spanBuilder =
          tracer.spanBuilder(spanName).setStartTimestamp(MICROSECONDS.toNanos(startTimeMicros));
      if (context == null) {
        spanBuilder.setNoParent();
      } else {
        spanBuilder.setParent(context);
      }
      span = spanBuilder.startSpan();
    }

    private AgentSpanImpl(final Span span) {
      this.span = span;
    }

    @Override
    public AgentSpan setAttribute(final String key, final boolean value) {
      span.setAttribute(key, value);
      return this;
    }

    @Override
    public AgentSpan setAttribute(final String key, final int value) {
      span.setAttribute(key, value);
      return this;
    }

    @Override
    public AgentSpan setAttribute(final String key, final long value) {
      span.setAttribute(key, value);
      return this;
    }

    @Override
    public AgentSpan setAttribute(final String key, final double value) {
      span.setAttribute(key, value);
      return this;
    }

    @Override
    public AgentSpan setAttribute(final String key, final String value) {
      if (value != null && !value.isEmpty()) {
        span.setAttribute(key, value);
      }
      return this;
    }

    @Override
    public AgentSpan setError(final boolean error) {
      span.setStatus(Status.UNKNOWN);
      return this;
    }

    @Override
    public AgentSpan setErrorMessage(final String errorMessage) {
      span.setAttribute(MoreTags.ERROR_MSG, errorMessage);
      return this;
    }

    @Override
    public AgentSpan addThrowable(final Throwable throwable) {
      final String message = throwable.getMessage();
      if (message != null) {
        span.setAttribute(MoreTags.ERROR_MSG, message);
      }
      span.setAttribute(MoreTags.ERROR_TYPE, throwable.getClass().getName());

      final StringWriter errorString = new StringWriter();
      throwable.printStackTrace(new PrintWriter(errorString));
      span.setAttribute(MoreTags.ERROR_STACK, errorString.toString());
      return this;
    }

    @Override
    public AgentContextImpl context() {
      final SpanContext context = span.getContext();
      return new AgentContextImpl(context);
    }

    @Override
    public void finish() {
      span.end();
    }

    @Override
    public String getSpanName() {
      if (span instanceof ReadableSpan) {
        return ((ReadableSpan) span).getName();
      } else {
        return "";
      }
    }

    @Override
    public void setSpanName(final String spanName) {
      span.updateName(spanName);
    }

    @Override
    public Span getSpan() {
      return span;
    }

    @Override
    public int hashCode() {
      return span.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
      if (!(obj instanceof AgentSpanImpl)) {
        return false;
      }
      final AgentSpanImpl other = (AgentSpanImpl) obj;
      return span.equals(other.span);
    }
  }

  private final class AgentScopeImpl implements AgentScope {

    private final AgentSpanImpl span;
    private final Scope scope;
    private final boolean finishSpanOnClose;

    private AgentScopeImpl(
        final AgentSpan span, final Scope scope, final boolean finishSpanOnClose) {
      assert span instanceof AgentSpanImpl;
      this.span = (AgentSpanImpl) span;
      this.scope = scope;
      this.finishSpanOnClose = finishSpanOnClose;
    }

    @Override
    public void close() {
      scope.close();
      if (finishSpanOnClose) {
        span.finish();
      }
    }

    @Override
    public AgentSpan span() {
      return span;
    }

    @Override
    public int hashCode() {
      return scope.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
      if (!(obj instanceof AgentScopeImpl)) {
        return false;
      }
      final AgentScopeImpl other = (AgentScopeImpl) obj;
      return scope.equals(other.scope);
    }
  }

  private final class AgentPropagationImpl implements AgentPropagation {

    @Override
    public <C> void inject(final AgentSpan span, final C carrier, final Setter<C> setter) {
      assert span instanceof AgentSpanImpl;
      tracer
          .getHttpTextFormat()
          .inject(
              ((AgentSpanImpl) span).getSpan().getContext(),
              carrier,
              new AgentPropagationImpl.Injector<>(setter));
    }

    private final class Injector<C> implements HttpTextFormat.Setter<C> {
      private final Setter<C> setter;

      private Injector(final Setter<C> setter) {
        this.setter = setter;
      }

      @Override
      public void put(final C carrier, final String key, final String value) {
        setter.set(carrier, key, value);
      }
    }

    @Override
    public <C> AgentSpan.Context extract(final C carrier, final Getter<C> getter) {
      SpanContext extract;
      try {
        extract = tracer.getHttpTextFormat().extract(carrier, new Extractor<>(getter));
      } catch (final IllegalArgumentException e) {
        extract = null;
      }
      return new AgentContextImpl(extract);
    }
  }

  private static final class Extractor<C> implements HttpTextFormat.Getter<C> {

    private final Getter<C> getter;

    private Extractor(final Getter<C> getter) {
      this.getter = getter;
    }

    @Override
    public String get(final C carrier, final String key) {
      return getter.get(carrier, key);
    }
  }

  private static final class AgentContextImpl implements AgentSpan.Context {
    private final SpanContext context;

    private AgentContextImpl(final SpanContext context) {
      this.context = context;
    }
  }
}
