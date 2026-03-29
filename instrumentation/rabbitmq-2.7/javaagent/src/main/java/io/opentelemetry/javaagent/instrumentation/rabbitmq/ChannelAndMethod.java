/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.google.auto.value.AutoValue;
import com.rabbitmq.client.Channel;
import java.util.Map;
import javax.annotation.Nullable;

@AutoValue
public abstract class ChannelAndMethod {

  public static ChannelAndMethod create(Channel channel, String method) {
    return new AutoValue_ChannelAndMethod(channel, method);
  }

  abstract Channel getChannel();

  abstract String getMethod();

  @Nullable private Map<String, Object> headers;

  @Nullable
  public Map<String, Object> getHeaders() {
    return headers;
  }

  public void setHeaders(@Nullable Map<String, Object> headers) {
    this.headers = headers;
  }
}
