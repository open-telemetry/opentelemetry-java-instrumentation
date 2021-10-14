/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import ratpack.func.Block;

public class BlockWrapper implements Block {

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
      // Skip wrapping, there is no need to propagate root context.
      return delegate;
    }
    return new BlockWrapper(delegate, context);
  }
}
