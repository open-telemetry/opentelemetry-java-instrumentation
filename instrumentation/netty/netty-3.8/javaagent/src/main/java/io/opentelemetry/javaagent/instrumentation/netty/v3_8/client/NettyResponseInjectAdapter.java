/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.jboss.netty.handler.codec.http.HttpHeaders;

public class NettyResponseInjectAdapter implements TextMapSetter<HttpHeaders> {

  public static final NettyResponseInjectAdapter SETTER = new NettyResponseInjectAdapter();

  @Override
  public void set(HttpHeaders headers, String key, String value) {
    headers.set(key, value);
  }
}
