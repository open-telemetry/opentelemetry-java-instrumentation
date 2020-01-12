package io.opentelemetry.auto.instrumentation.ratpack;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;

import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import lombok.extern.slf4j.Slf4j;
import ratpack.func.Action;

@Slf4j
public class ActionWrapper<T> implements Action<T> {
  private final Action<T> delegate;
  private final AgentSpan span;

  private ActionWrapper(final Action<T> delegate, final AgentSpan span) {
    assert span != null;
    this.delegate = delegate;
    this.span = span;
  }

  @Override
  public void execute(final T t) throws Exception {
    try (final AgentScope scope = activateSpan(span, false)) {
      delegate.execute(t);
    }
  }

  public static <T> Action<T> wrapIfNeeded(final Action<T> delegate, final AgentSpan span) {
    if (delegate instanceof ActionWrapper || span == null) {
      return delegate;
    }
    log.debug("Wrapping action task {}", delegate);
    return new ActionWrapper(delegate, span);
  }
}
