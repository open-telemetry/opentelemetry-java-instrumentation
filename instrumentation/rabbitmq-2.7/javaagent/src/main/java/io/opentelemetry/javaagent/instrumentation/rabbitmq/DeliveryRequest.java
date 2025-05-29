/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.google.auto.value.AutoValue;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Envelope;

@AutoValue
abstract class DeliveryRequest {

  static DeliveryRequest create(
      String queue,
      Envelope envelope,
      Connection connection,
      AMQP.BasicProperties properties,
      byte[] body) {
    return new AutoValue_DeliveryRequest(queue, envelope, connection, properties, body);
  }

  abstract String getQueue();

  abstract Envelope getEnvelope();

  abstract Connection getConnection();

  abstract AMQP.BasicProperties getProperties();

  @SuppressWarnings("mutable")
  abstract byte[] getBody();

  String spanName() {
    String queue = getQueue();
    if (queue == null || queue.isEmpty()) {
      return "<default> process";
    } else if (queue.startsWith("amq.gen-") || queue.startsWith("spring.gen-")) {
      // The spring.gen-<random uid> name comes from AnonymousQueue in the Spring AMQP library
      return "<generated> process";
    } else {
      return queue + " process";
    }
  }
}
