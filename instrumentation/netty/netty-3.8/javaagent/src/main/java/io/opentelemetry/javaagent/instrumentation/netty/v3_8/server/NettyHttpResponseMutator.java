/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import org.jboss.netty.handler.codec.http.HttpResponse;

public enum NettyHttpResponseMutator implements HttpServerResponseMutator<HttpResponse> {
  INSTANCE;

  NettyHttpResponseMutator() {}

  @Override
  public void appendHeader(HttpResponse response, String name, String value) {
    response.headers().add(name, value);
  }
}
