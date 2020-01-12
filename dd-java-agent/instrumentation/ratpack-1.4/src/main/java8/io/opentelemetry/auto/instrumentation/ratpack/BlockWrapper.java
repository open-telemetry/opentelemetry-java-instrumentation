package io.opentelemetry.auto.instrumentation.ratpack;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;

import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
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
