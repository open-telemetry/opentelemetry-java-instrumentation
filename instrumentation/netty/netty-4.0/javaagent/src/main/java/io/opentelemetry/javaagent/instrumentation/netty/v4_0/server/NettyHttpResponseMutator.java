/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.server;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;

public enum NettyHttpResponseMutator implements HttpServerResponseMutator<HttpResponse> {
  INSTANCE;

  NettyHttpResponseMutator() {}

  @Override
  public void appendHeader(HttpResponse response, String name, String value) {
    response.headers().add(name, value);
  }
}
