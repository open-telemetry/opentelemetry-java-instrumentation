/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import com.google.auto.value.AutoValue;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Envelope;

@AutoValue
abstract class DeliveryRequest {

  static DeliveryRequest create(
      String queue, Envelope envelope, Connection connection, AMQP.BasicProperties properties) {
    return new AutoValue_DeliveryRequest(queue, envelope, connection, properties);
  }

  abstract String getQueue();

  abstract Envelope getEnvelope();

  abstract Connection getConnection();

  abstract AMQP.BasicProperties getProperties();
}
