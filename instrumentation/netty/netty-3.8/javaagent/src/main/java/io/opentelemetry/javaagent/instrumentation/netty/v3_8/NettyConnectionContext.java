/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

public class NettyConnectionContext {
  private Context connectionContext;

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
}
