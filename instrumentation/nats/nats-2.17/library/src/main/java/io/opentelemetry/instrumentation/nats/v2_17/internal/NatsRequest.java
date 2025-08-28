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

  public static NatsRequest create(Connection connection, Message message) {
    return create(
        message.getConnection() == null ? connection : message.getConnection(),
        message.getReplyTo(),
        message.getSubject(),
        message.getHeaders(),
        message.getData());
  }

  public static NatsRequest create(
      Connection connection, String replyTo, String subject, Headers headers, byte[] data) {
    return new AutoValue_NatsRequest(
        replyTo, connection.getServerInfo().getClientId(), subject, headers, getDataSize(data));
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
}
