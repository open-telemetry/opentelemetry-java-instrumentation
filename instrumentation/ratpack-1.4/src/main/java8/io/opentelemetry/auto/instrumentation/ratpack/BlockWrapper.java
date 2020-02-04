package io.opentelemetry.auto.instrumentation.ratpack;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import ratpack.func.Block;

@Slf4j
public class BlockWrapper implements Block {
  private static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  private final Block delegate;
  private final Span span;

  private BlockWrapper(final Block delegate, final Span span) {
    assert span != null;
    this.delegate = delegate;
    this.span = span;
  }

  @Override
  public void execute() throws Exception {
    try (final Scope scope = TRACER.withSpan(span)) {
      delegate.execute();
    }
  }

  public static Block wrapIfNeeded(final Block delegate) {
    final Span span = TRACER.getCurrentSpan();
    if (delegate instanceof BlockWrapper || !span.getContext().isValid()) {
      return delegate;
    }
    log.debug("Wrapping block {}", delegate);
    return new BlockWrapper(delegate, span);
  }
}
