/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.ratpack;

import static io.opentelemetry.context.ContextUtils.withScopedContext;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.func.Block;

public class BlockWrapper implements Block {

  private static final Logger log = LoggerFactory.getLogger(BlockWrapper.class);

  private static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto.ratpack-1.4");

  private final Block delegate;
  private final Context parentContext;

  private BlockWrapper(Block delegate, Context parentContext) {
    assert parentContext != null;
    this.delegate = delegate;
    this.parentContext = parentContext;
  }

  @Override
  public void execute() throws Exception {
    try (Scope ignored = withScopedContext(parentContext)) {
      delegate.execute();
    }
  }

  public static Block wrapIfNeeded(Block delegate) {
    if (delegate instanceof BlockWrapper) {
      return delegate;
    }
    log.debug("Wrapping block {}", delegate);
    return new BlockWrapper(delegate, Context.current());
  }
}
