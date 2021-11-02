/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import javax.annotation.Nullable;

final class HttpRequestHeadersSetter implements TextMapSetter<HttpRequestAndChannel> {

  @Override
  public void set(@Nullable HttpRequestAndChannel carrier, String key, String value) {
    carrier.request().headers().set(key, value);
  }
}
