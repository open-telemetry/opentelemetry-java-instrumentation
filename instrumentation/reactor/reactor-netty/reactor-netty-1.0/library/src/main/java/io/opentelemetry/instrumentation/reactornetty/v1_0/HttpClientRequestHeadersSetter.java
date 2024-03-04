/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactornetty.v1_0;

import javax.annotation.Nullable;

import io.opentelemetry.context.propagation.TextMapSetter;
import reactor.netty.http.client.HttpClientRequest;

public enum HttpClientRequestHeadersSetter implements TextMapSetter<HttpClientRequest> {
  INSTANCE;

  @Override
  public void set(@Nullable HttpClientRequest request, String key, String value) {
    request.header(key, value);
  }
}
