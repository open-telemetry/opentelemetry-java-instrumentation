/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.client;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.netty.v4.common.internal.HttpRequestAndChannel;

enum HttpRequestHeadersSetter implements TextMapSetter<HttpRequestAndChannel> {
  INSTANCE;

  @Override
  public void set(HttpRequestAndChannel requestAndChannel, String key, String value) {
    requestAndChannel.request().headers().set(key, value);
  }
}
