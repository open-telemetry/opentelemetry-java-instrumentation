/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import com.google.auto.value.AutoValue;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpRequest;

@AutoValue
public abstract class NettyRequest {

  public static NettyRequest create(HttpRequest request, Channel channel) {
    return new AutoValue_NettyRequest(request, channel);
  }

  public abstract HttpRequest request();

  public abstract Channel channel();
}
