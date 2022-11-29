/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.google.auto.value.AutoValue;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;
import javax.annotation.Nullable;

@AutoValue
public abstract class ReceiveRequest {

  public static ReceiveRequest create(String queue, GetResponse response, Connection connection) {
    return new AutoValue_ReceiveRequest(queue, response, connection);
  }

  public abstract String getQueue();

  @Nullable
  public abstract GetResponse getResponse();

  public abstract Connection getConnection();

  String spanName() {
    String queue = getQueue();
    return (queue.startsWith("amq.gen-") ? "<generated>" : queue) + " receive";
  }
}
