/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4.common.client;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.instrumentation.netty.v4.common.HttpRequestAndChannel;

enum HttpRequestHeadersSetter implements TextMapSetter<HttpRequestAndChannel> {
  INSTANCE;

  @Override
  public void set(HttpRequestAndChannel requestAndChannel, String key, String value) {
    requestAndChannel.request().headers().set(key, value);
  }
}
