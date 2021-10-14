/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common;

import com.google.auto.value.AutoValue;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;

@AutoValue
public abstract class HttpRequestAndChannel {

  public static HttpRequestAndChannel create(HttpRequest request, Channel channel) {
    return new AutoValue_HttpRequestAndChannel(request, channel);
  }

  public abstract HttpRequest request();

  public abstract Channel channel();
}
