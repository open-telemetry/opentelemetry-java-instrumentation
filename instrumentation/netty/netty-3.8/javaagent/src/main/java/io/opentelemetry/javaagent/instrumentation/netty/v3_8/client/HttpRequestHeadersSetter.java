/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyRequest;
import javax.annotation.Nullable;

class HttpRequestHeadersSetter implements TextMapSetter<NettyRequest> {

  @Override
  public void set(@Nullable NettyRequest carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.request().headers().set(key, value);
  }
}
