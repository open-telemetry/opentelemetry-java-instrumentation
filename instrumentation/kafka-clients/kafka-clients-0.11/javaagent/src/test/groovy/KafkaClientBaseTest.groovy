/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.IntegerDeserializer
import org.apache.kafka.common.serialization.IntegerSerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.containers.KafkaContainer
import spock.lang.Shared
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.TimeUnit

abstract class KafkaClientBaseTest extends AgentInstrumentationSpecification {

  protected static final SHARED_TOPIC = "shared.topic"

  private static final boolean propagationEnabled = Boolean.parseBoolean(
    System.getProperty("otel.instrumentation.kafka.client-propagation.enabled", "true"))

  @Shared
  static KafkaContainer kafka
  @Shared
  static Producer<Integer, String> producer
  @Shared
  static Consumer<Integer, String> consumer

  def setupSpec() {
    kafka = new KafkaContainer()
    kafka.start()

    // create test topic
    AdminClient.create(["bootstrap.servers": kafka.bootstrapServers]).withCloseable { admin ->
      admin.createTopics([new NewTopic(SHARED_TOPIC, 1, (short) 1)]).all().get(10, TimeUnit.SECONDS)
    }

    // values copied from spring's KafkaTestUtils
    def producerProps = [
      "bootstrap.servers": kafka.bootstrapServers,
      "retries"          : 0,
      "batch.size"       : "16384",
      "linger.ms"        : 1,
      "buffer.memory"    : "33554432",
      "key.serializer"   : IntegerSerializer.class,
      "value.serializer" : StringSerializer.class
    ]
    producer = new KafkaProducer<>(producerProps)

    // values copied from spring's KafkaTestUtils
    def consumerProps = [
      "bootstrap.servers"      : kafka.bootstrapServers,
      "group.id"               : "test",
      "enable.auto.commit"     : "false",
      "auto.commit.interval.ms": "10",
      "session.timeout.ms"     : "60000",
      "key.deserializer"       : IntegerDeserializer.class,
      "value.deserializer"     : StringDeserializer.class
    ]
    consumer = new KafkaConsumer<>(consumerProps)

    // assign only existing topic partition
    consumer.assign([new TopicPartition(SHARED_TOPIC, 0)])
  }

  def cleanupSpec() {
    consumer?.close()
    producer?.close()
    kafka.stop()
  }

  @Unroll
  def "test kafka client header propagation manual config"() {
    when:
    String message = "Testing without headers"
    producer.send(new ProducerRecord<>(SHARED_TOPIC, message))

    then:
    // check that the message was received
    def records = consumer.poll(Duration.ofSeconds(5).toMillis())
    for (record in records) {
      assert record.headers().iterator().hasNext() == propagationEnabled
    }
  }
}
