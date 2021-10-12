/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import org.checkerframework.checker.nullness.qual.Nullable;

@AutoValue
public abstract class NettyRequestContexts {

  public static NettyRequestContexts create(@Nullable Context parentContext, Context context) {
    return new AutoValue_NettyRequestContexts(parentContext, context);
  }

  @Nullable
  public abstract Context parentContext();

  public abstract Context context();
}
