/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import io.netty.handler.codec.http.HttpHeaders;
import io.opentelemetry.context.propagation.TextMapSetter;

public class NettyResponseInjectAdapter implements TextMapSetter<HttpHeaders> {

  public static final NettyResponseInjectAdapter SETTER = new NettyResponseInjectAdapter();

  @Override
  public void set(HttpHeaders headers, String key, String value) {
    headers.set(key, value);
  }
}
