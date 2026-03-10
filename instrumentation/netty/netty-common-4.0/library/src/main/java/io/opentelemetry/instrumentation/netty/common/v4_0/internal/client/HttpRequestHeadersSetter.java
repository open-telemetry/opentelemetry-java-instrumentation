/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.common.v4_0.internal.client;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;

enum HttpRequestHeadersSetter implements TextMapSetter<NettyCommonRequest> {
  INSTANCE;

  @Override
  public void set(NettyCommonRequest requestAndChannel, String key, String value) {
    requestAndChannel.getRequest().headers().set(key, value);
  }
}
