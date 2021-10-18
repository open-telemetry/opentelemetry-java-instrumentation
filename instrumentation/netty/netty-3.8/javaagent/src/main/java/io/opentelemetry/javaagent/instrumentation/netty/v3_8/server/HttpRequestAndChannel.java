/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import com.google.auto.value.AutoValue;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpRequest;

@AutoValue
abstract class HttpRequestAndChannel {

  public static HttpRequestAndChannel create(HttpRequest request, Channel channel) {
    return new AutoValue_HttpRequestAndChannel(request, channel);
  }

  abstract HttpRequest request();

  abstract Channel channel();
}
