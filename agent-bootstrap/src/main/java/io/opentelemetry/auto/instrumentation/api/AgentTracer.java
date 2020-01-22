package io.opentelemetry.auto.instrumentation.api;

import io.opentelemetry.auto.instrumentation.api.AgentSpan.Context;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicReference;

@Deprecated
public class AgentTracer {

  // Implicit parent
  public static AgentSpan startSpan(final String spanName) {
    return get().startSpan(spanName);
  }

  // Implicit parent
  public static AgentSpan startSpan(final String spanName, final long startTimeMicros) {
    return get().startSpan(spanName, startTimeMicros);
  }

  // Explicit parent
  public static AgentSpan startSpan(final String spanName, final AgentSpan.Context parent) {
    return get().startSpan(spanName, parent);
  }

  // Explicit parent
  public static AgentSpan startSpan(
      final String spanName, final AgentSpan.Context parent, final long startTimeMicros) {
    return get().startSpan(spanName, parent, startTimeMicros);
  }

  public static AgentScope activateSpan(final AgentSpan span, final boolean finishSpanOnClose) {
    return get().activateSpan(span, finishSpanOnClose);
  }

  public static AgentSpan activeSpan() {
    return get().activeSpan();
  }

  public static AgentPropagation propagate() {
    return get().propagate();
  }

  public static AgentSpan noopSpan() {
    return get().noopSpan();
  }

  private static final TracerAPI DEFAULT = new NoopTracerAPI();

  private static final AtomicReference<TracerAPI> provider = new AtomicReference<>(DEFAULT);

  public static void registerIfAbsent(final TracerAPI trace) {
    provider.compareAndSet(DEFAULT, trace);
  }

  public static TracerAPI get() {
    return provider.get();
  }

  // Not intended to be constructed.
  private AgentTracer() {}

  public interface TracerAPI {
    AgentSpan startSpan(String spanName);

    AgentSpan startSpan(String spanName, long startTimeMicros);

    AgentSpan startSpan(String spanName, AgentSpan.Context parent);

    AgentSpan startSpan(String spanName, AgentSpan.Context parent, long startTimeMicros);

    AgentScope activateSpan(AgentSpan span, boolean finishSpanOnClose);

    AgentSpan activeSpan();

    AgentPropagation propagate();

    AgentSpan noopSpan();
  }

  static class NoopTracerAPI implements TracerAPI {

    protected NoopTracerAPI() {}

    @Override
    public AgentSpan startSpan(final String spanName) {
      return NoopAgentSpan.INSTANCE;
    }

    @Override
    public AgentSpan startSpan(final String spanName, final long startTimeMicros) {
      return NoopAgentSpan.INSTANCE;
    }

    @Override
    public AgentSpan startSpan(final String spanName, final Context parent) {
      return NoopAgentSpan.INSTANCE;
    }

    @Override
    public AgentSpan startSpan(
        final String spanName, final Context parent, final long startTimeMicros) {
      return NoopAgentSpan.INSTANCE;
    }

    @Override
    public AgentScope activateSpan(final AgentSpan span, final boolean finishSpanOnClose) {
      return NoopAgentScope.INSTANCE;
    }

    @Override
    public AgentSpan activeSpan() {
      return NoopAgentSpan.INSTANCE;
    }

    @Override
    public AgentPropagation propagate() {
      return NoopAgentPropagation.INSTANCE;
    }

    @Override
    public AgentSpan noopSpan() {
      return NoopAgentSpan.INSTANCE;
    }
  }

  static class NoopAgentSpan implements AgentSpan {
    static final NoopAgentSpan INSTANCE = new NoopAgentSpan();

    @Override
    public AgentSpan setAttribute(final String key, final boolean value) {
      return this;
    }

    @Override
    public AgentSpan setAttribute(final String key, final int value) {
      return this;
    }

    @Override
    public AgentSpan setAttribute(final String key, final long value) {
      return this;
    }

    @Override
    public AgentSpan setAttribute(final String key, final double value) {
      return this;
    }

    @Override
    public AgentSpan setAttribute(final String key, final String value) {
      return this;
    }

    @Override
    public AgentSpan setError(final boolean error) {
      return this;
    }

    @Override
    public AgentSpan setErrorMessage(final String errorMessage) {
      return this;
    }

    @Override
    public AgentSpan addThrowable(final Throwable throwable) {
      return this;
    }

    @Override
    public Context context() {
      return NoopContext.INSTANCE;
    }

    @Override
    public void finish() {}

    @Override
    public String getSpanName() {
      return "";
    }

    @Override
    public void setSpanName(final String spanName) {}

    @Override
    public Span getSpan() {
      return DefaultSpan.getInvalid();
    }
  }

  public static class NoopAgentScope implements AgentScope {
    public static final NoopAgentScope INSTANCE = new NoopAgentScope();

    @Override
    public AgentSpan span() {
      return NoopAgentSpan.INSTANCE;
    }

    @Override
    public void close() {}
  }

  static class NoopAgentPropagation implements AgentPropagation {
    static final NoopAgentPropagation INSTANCE = new NoopAgentPropagation();

    @Override
    public <C> void inject(final AgentSpan span, final C carrier, final Setter<C> setter) {}

    @Override
    public <C> Context extract(final C carrier, final Getter<C> getter) {
      return NoopContext.INSTANCE;
    }
  }

  static class NoopContext implements Context {
    static final NoopContext INSTANCE = new NoopContext();
  }
}
