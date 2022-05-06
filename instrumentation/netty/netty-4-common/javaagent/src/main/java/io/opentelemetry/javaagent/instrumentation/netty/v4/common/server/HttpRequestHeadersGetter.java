/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4.common.server;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.javaagent.instrumentation.netty.v4.common.HttpRequestAndChannel;
import javax.annotation.Nullable;

enum HttpRequestHeadersGetter implements TextMapGetter<HttpRequestAndChannel> {
  INSTANCE;

  @Override
  public Iterable<String> keys(HttpRequestAndChannel carrier) {
    return carrier.request().headers().names();
  }

  @Nullable
  @Override
  public String get(@Nullable HttpRequestAndChannel carrier, String key) {
    return carrier.request().headers().get(key);
  }
}
