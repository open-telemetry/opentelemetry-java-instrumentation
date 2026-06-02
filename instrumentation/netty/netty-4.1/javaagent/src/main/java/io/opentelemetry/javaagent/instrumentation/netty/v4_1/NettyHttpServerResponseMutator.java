/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;

public enum NettyHttpServerResponseMutator implements HttpServerResponseMutator<HttpResponse> {
  INSTANCE;

  @Override
  public void appendHeader(HttpResponse response, String name, String value) {
    response.headers().add(name, value);
  }
}
