/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyRequest;

@AutoValue
abstract class NettyServerRequestAndContext {

  static NettyServerRequestAndContext create(NettyRequest request, Context context) {
    return new AutoValue_NettyServerRequestAndContext(request, context);
  }

  abstract NettyRequest request();

  abstract Context context();
}
