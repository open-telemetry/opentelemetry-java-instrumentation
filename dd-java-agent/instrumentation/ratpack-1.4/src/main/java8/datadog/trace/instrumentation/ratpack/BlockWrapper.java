package datadog.trace.instrumentation.ratpack;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import lombok.extern.slf4j.Slf4j;
import ratpack.func.Block;

@Slf4j
public class BlockWrapper implements Block {
  private final Block delegate;
  private final AgentSpan span;

  private BlockWrapper(final Block delegate, final AgentSpan span) {
    assert span != null;
    this.delegate = delegate;
    this.span = span;
  }

  @Override
  public void execute() throws Exception {
    try (final AgentScope scope = activateSpan(span, false)) {
      scope.setAsyncPropagation(true);
      delegate.execute();
    }
  }

  public static Block wrapIfNeeded(final Block delegate, final AgentSpan span) {
    if (delegate instanceof BlockWrapper || span == null) {
      return delegate;
    }
    log.debug("Wrapping block {}", delegate);
    return new BlockWrapper(delegate, span);
  }
}
