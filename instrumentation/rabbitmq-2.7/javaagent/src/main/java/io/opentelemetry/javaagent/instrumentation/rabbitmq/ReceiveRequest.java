/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.google.auto.value.AutoValue;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;
import java.time.Instant;
import javax.annotation.Nullable;

@AutoValue
public abstract class ReceiveRequest {

  public static ReceiveRequest create(
      String queue, Timer timer, GetResponse response, Connection connection) {
    return new AutoValue_ReceiveRequest(queue, timer, response, connection);
  }

  public abstract String getQueue();

  public abstract Timer getTimer();

  @Nullable
  public abstract GetResponse getResponse();

  public abstract Connection getConnection();

  String spanName() {
    String queue = getQueue();
    return (queue.startsWith("amq.gen-") ? "<generated>" : queue) + " receive";
  }

  Instant startTime() {
    return getTimer().startTime();
  }

  Instant now() {
    return getTimer().now();
  }
}
