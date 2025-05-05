/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21.internal;

import com.google.auto.value.AutoValue;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.", or "This class is internal and experimental. Its APIs are unstable and can change at
 * any time. Its APIs (or a version of them) may be promoted to the public stable API in the future,
 * but no guarantees are made.
 */
@AutoValue
public abstract class NatsRequest {

  public static NatsRequest create(Connection connection, String subject) {
    return create(connection, subject, null);
  }

  public static NatsRequest create(Connection connection, String subject, byte[] data) {
    return create(connection, subject, null, data);
  }

  public static NatsRequest create(Connection connection, Message message) {
    return create(
        message.getConnection() == null ? connection : message.getConnection(),
        message.getSubject(),
        message.getHeaders(),
        message.getData());
  }

  public static NatsRequest create(
      Connection connection, String subject, Headers headers, byte[] data) {
    return new AutoValue_NatsRequest(
        connection.getServerInfo().getClientId(), subject, headers, getDataSize(data));
  }

  public abstract int getClientId();

  public abstract String getSubject();

  @Nullable
  public abstract Headers getHeaders();

  public abstract long getDataSize();

  private static long getDataSize(byte[] data) {
    return data == null ? 0 : data.length;
  }
}
