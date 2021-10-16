/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;

final class HttpRequestHeadersSetter implements TextMapSetter<HttpRequestAndChannel> {

  @Override
  public void set(HttpRequestAndChannel requestAndChannel, String key, String value) {
    requestAndChannel.request().headers().set(key, value);
  }
}
