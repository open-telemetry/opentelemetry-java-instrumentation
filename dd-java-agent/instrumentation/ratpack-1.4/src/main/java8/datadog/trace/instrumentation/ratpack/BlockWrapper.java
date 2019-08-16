package datadog.trace.instrumentation.ratpack;

import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import lombok.extern.slf4j.Slf4j;
import ratpack.func.Block;

@Slf4j
public class BlockWrapper implements Block {
  private final Block delegate;
  private final Span span;

  private BlockWrapper(final Block delegate, final Span span) {
    assert span != null;
    this.delegate = delegate;
    this.span = span;
  }

  @Override
  public void execute() throws Exception {
    try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }
      delegate.execute();
    }
  }

  public static Block wrapIfNeeded(final Block delegate, final Span span) {
    if (delegate instanceof BlockWrapper || span == null) {
      return delegate;
    }
    log.debug("Wrapping block {}", delegate);
    return new BlockWrapper(delegate, span);
  }
}
