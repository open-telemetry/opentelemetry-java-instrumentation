/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.common.v4_0.internal.client;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import javax.annotation.Nullable;

final class HttpRequestHeadersSetter implements TextMapSetter<NettyCommonRequest> {

  @Override
  public void set(@Nullable NettyCommonRequest requestAndChannel, String key, String value) {
    if (requestAndChannel == null) {
      return;
    }
    requestAndChannel.getRequest().headers().set(key, value);
  }
}
