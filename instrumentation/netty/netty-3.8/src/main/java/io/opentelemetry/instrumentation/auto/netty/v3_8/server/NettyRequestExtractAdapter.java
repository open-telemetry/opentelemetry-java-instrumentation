/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.netty.v3_8.server;

import io.opentelemetry.context.propagation.TextMapPropagator;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class NettyRequestExtractAdapter implements TextMapPropagator.Getter<HttpRequest> {

  public static final NettyRequestExtractAdapter GETTER = new NettyRequestExtractAdapter();

  @Override
  public String get(HttpRequest request, String key) {
    return request.headers().get(key);
  }
}
