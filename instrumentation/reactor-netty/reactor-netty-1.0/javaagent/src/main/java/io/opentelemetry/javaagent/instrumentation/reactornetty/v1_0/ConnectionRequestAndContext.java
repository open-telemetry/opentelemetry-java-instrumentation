/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;

@AutoValue
public abstract class ConnectionRequestAndContext {

  public static ConnectionRequestAndContext create(
      NettyConnectionRequest request, Context context) {
    return new AutoValue_ConnectionRequestAndContext(request, context);
  }

  public abstract NettyConnectionRequest request();

  public abstract Context context();
}
