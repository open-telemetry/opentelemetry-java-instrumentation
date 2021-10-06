/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import io.opentelemetry.context.Context;
import org.checkerframework.checker.nullness.qual.Nullable;

public class NettyConnectionContext {
  private Context connectionContext;
  private boolean connectionSpanCreated;

  public NettyConnectionContext(Context connectionContext) {
    this.connectionContext = connectionContext;
  }

  @Nullable
  public Context get() {
    return connectionContext;
  }

  @Nullable
  public Context remove() {
    Context context = this.connectionContext;
    connectionContext = null;
    return context;
  }

  public boolean createConnectionSpan() {
    if (connectionSpanCreated) {
      return false;
    }
    connectionSpanCreated = true;
    return true;
  }
}
