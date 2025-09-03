/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v4_0;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.vertx.kafka.AbstractSingleRecordVertxKafkaTest;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;
import org.junit.jupiter.api.extension.RegisterExtension;

class SingleRecordVertxKafka4Test extends AbstractSingleRecordVertxKafkaTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected boolean hasConsumerGroup() {
    return true;
  }

  @Override
  protected void closeKafkaConsumer(KafkaConsumer<?, ?> consumer) {
    consumer.close();
  }

  @Override
  protected void closeKafkaProducer(KafkaProducer<?, ?> producer) {
    producer.close();
  }

  @Override
  protected void closeVertx(Vertx vertx) {
    vertx.close();
  }

  @Override
  protected void sendRecord(
      KafkaProducerRecord<String, String> record, Handler<AsyncResult<RecordMetadata>> handler) {
    kafkaProducer.send(record, handler);
  }

  @Override
  protected void subscribe(String topic) {
    kafkaConsumer.subscribe(topic);
  }
}
