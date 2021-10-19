/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import javax.annotation.Nullable;

@AutoValue
abstract class NettyClientRequestAndContexts {

  public static NettyClientRequestAndContexts create(
      @Nullable Context parentContext, Context context, HttpRequestAndChannel request) {
    return new AutoValue_NettyClientRequestAndContexts(parentContext, context, request);
  }

  abstract Context parentContext();

  abstract Context context();

  abstract HttpRequestAndChannel request();
}
