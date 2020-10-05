/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.ratpack;

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.func.Block;

public class BlockWrapper implements Block {

  private static final Logger log = LoggerFactory.getLogger(BlockWrapper.class);

  private static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto.ratpack-1.4");

  private final Block delegate;
  private final Span span;

  private BlockWrapper(Block delegate, Span span) {
    assert span != null;
    this.delegate = delegate;
    this.span = span;
  }

  @Override
  public void execute() throws Exception {
    try (Scope scope = currentContextWith(span)) {
      delegate.execute();
    }
  }

  public static Block wrapIfNeeded(Block delegate) {
    Span span = TRACER.getCurrentSpan();
    if (delegate instanceof BlockWrapper || !span.getContext().isValid()) {
      return delegate;
    }
    log.debug("Wrapping block {}", delegate);
    return new BlockWrapper(delegate, span);
  }
}
