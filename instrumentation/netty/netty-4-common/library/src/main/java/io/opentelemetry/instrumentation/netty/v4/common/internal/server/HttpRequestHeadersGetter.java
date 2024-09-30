/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.server;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum HttpRequestHeadersGetter implements TextMapGetter<HttpRequestAndChannel> {
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
