/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.func.Block;

public class BlockWrapper implements Block {

  private static final Logger logger = LoggerFactory.getLogger(BlockWrapper.class);

  private final Block delegate;
  private final Context parentContext;

  private BlockWrapper(Block delegate, Context parentContext) {
    assert parentContext != null;
    this.delegate = delegate;
    this.parentContext = parentContext;
  }

  @Override
  public void execute() throws Exception {
    try (Scope ignored = parentContext.makeCurrent()) {
      delegate.execute();
    }
  }

  public static Block wrapIfNeeded(Block delegate) {
    if (delegate instanceof BlockWrapper) {
      return delegate;
    }
    Context context = Context.current();
    if (context == Context.root()) {
      return delegate;
    }
    logger.debug("Wrapping block {}", delegate);
    return new BlockWrapper(delegate, context);
  }
}
