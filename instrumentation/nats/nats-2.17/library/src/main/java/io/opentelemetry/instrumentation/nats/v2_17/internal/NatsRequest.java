/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import com.google.auto.value.AutoValue;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@AutoValue
public abstract class NatsRequest {

  public static NatsRequest create(
      Connection connection, String subject, String replyTo, Headers headers, byte[] body) {
    return new AutoValue_NatsRequest(
        replyTo,
        connection.getServerInfo().getClientId(),
        subject,
        headers,
        getDataSize(body),
        connection.getOptions().getInboxPrefix());
  }

  public static NatsRequest create(Connection connection, Message message) {
    return new AutoValue_NatsRequest(
        message.getReplyTo(),
        connection.getServerInfo().getClientId(),
        message.getSubject(),
        message.getHeaders(),
        getDataSize(message.getData()),
        connection.getOptions().getInboxPrefix());
  }

  @Nullable
  public abstract String getReplyTo();

  public abstract int getClientId();

  public abstract String getSubject();

  @Nullable
  public abstract Headers getHeaders();

  public abstract long getDataSize();

  private static long getDataSize(byte[] data) {
    return data == null ? 0 : data.length;
  }

  public abstract String getInboxPrefix();
}
