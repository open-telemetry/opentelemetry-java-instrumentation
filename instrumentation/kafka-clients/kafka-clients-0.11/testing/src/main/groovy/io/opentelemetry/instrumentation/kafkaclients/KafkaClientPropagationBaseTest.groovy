/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients

import io.opentelemetry.instrumentation.test.AgentTestTrait
import org.apache.kafka.clients.producer.ProducerRecord
import spock.lang.Unroll

abstract class KafkaClientPropagationBaseTest extends KafkaClientBaseTest implements AgentTestTrait {

  private static final boolean propagationEnabled = Boolean.parseBoolean(
    System.getProperty("otel.instrumentation.kafka.client-propagation.enabled", "true"))

  @Unroll
  def "test kafka client header propagation manual config"() {
    when:
    String message = "Testing without headers"
    producer.send(new ProducerRecord<>(SHARED_TOPIC, message))

    then:
    // check that the message was received
    def records = records(1)
    for (record in records) {
      assert record.headers().iterator().hasNext() == propagationEnabled
    }
  }
}