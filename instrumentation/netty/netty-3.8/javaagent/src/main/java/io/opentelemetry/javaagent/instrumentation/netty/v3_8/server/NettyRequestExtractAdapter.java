/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import io.opentelemetry.context.propagation.TextMapGetter;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class NettyRequestExtractAdapter implements TextMapGetter<HttpRequest> {

  public static final NettyRequestExtractAdapter GETTER = new NettyRequestExtractAdapter();

  @Override
  public Iterable<String> keys(HttpRequest request) {
    return request.headers().names();
  }

  @Override
  public String get(HttpRequest request, String key) {
    return request.headers().get(key);
  }
}
