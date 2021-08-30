/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runInternalSpan

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.Acknowledgment
import org.testcontainers.containers.KafkaContainer
import spock.lang.Shared

class SpringKafkaInstrumentationTest extends AgentInstrumentationSpecification {
  @Shared
  static KafkaContainer kafka
  @Shared
  static ConfigurableApplicationContext applicationContext

  def setupSpec() {
    kafka = new KafkaContainer()
    kafka.start()

    def app = new SpringApplication(ConsumerConfig)
    app.setDefaultProperties([
      "spring.jmx.enabled"                         : false,
      "spring.main.web-application-type"           : "none",
      "spring.kafka.bootstrap-servers"             : kafka.bootstrapServers,
      "spring.kafka.consumer.auto-offset-reset"    : "earliest",
      "spring.kafka.consumer.linger-ms"            : 10,
      // wait 2s between poll() calls
      "spring.kafka.listener.idle-between-polls"   : 2000,
      "spring.kafka.producer.transaction-id-prefix": "test-",
    ])
    applicationContext = app.run()
  }

  def cleanupSpec() {
    kafka.stop()
    applicationContext?.stop()
  }

  def "should create spans for batch receive+process"() {
    given:
    def kafkaTemplate = applicationContext.getBean("kafkaTemplate", KafkaTemplate)

    when:
    runWithSpan("producer") {
      // wrapping in a transaction is needed to remove the possibility of messages being picked up separately by the consumer
      kafkaTemplate.executeInTransaction({ ops ->
        ops.send("testTopic", "10", "testSpan1")
        ops.send("testTopic", "20", "testSpan2")
      })
    }

    then:
    assertTraces(2) {
      SpanData consumer1, consumer2

      trace(0, 5) {
        span(0) {
          name "producer"
        }
        span(1) {
          name "testTopic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" "testTopic"
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }
        span(2) {
          name "testTopic receive"
          kind CONSUMER
          childOf span(1)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" "testTopic"
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "receive"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "${SemanticAttributes.MESSAGING_KAFKA_PARTITION.key}" 0
            "kafka.offset" Long
            "kafka.record.queue_time_ms" Long
          }
        }
        span(3) {
          name "testTopic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" "testTopic"
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }
        span(4) {
          name "testTopic receive"
          kind CONSUMER
          childOf span(3)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" "testTopic"
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "receive"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "${SemanticAttributes.MESSAGING_KAFKA_PARTITION.key}" 0
            "kafka.offset" Long
            "kafka.record.queue_time_ms" Long
          }
        }

        consumer1 = span(2)
        consumer2 = span(4)
      }
      trace(1, 2) {
        span(0) {
          name "testTopic process"
          kind CONSUMER
          hasLink consumer1
          hasLink consumer2
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" "testTopic"
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
          }
        }
        span(1) {
          name "consumer"
          childOf span(0)
        }
      }
    }
  }

  def "should handle failure in Kafka listener"() {
    given:
    def kafkaTemplate = applicationContext.getBean("kafkaTemplate", KafkaTemplate)

    when:
    runWithSpan("producer") {
      kafkaTemplate.executeInTransaction({ ops ->
        ops.send("testTopic", "10", "error")
      })
    }

    then:
    assertTraces(2) {
      SpanData consumer

      trace(0, 3) {
        span(0) {
          name "producer"
        }
        span(1) {
          name "testTopic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" "testTopic"
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }
        span(2) {
          name "testTopic receive"
          kind CONSUMER
          childOf span(1)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" "testTopic"
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "receive"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "${SemanticAttributes.MESSAGING_KAFKA_PARTITION.key}" 0
            "kafka.offset" Long
            "kafka.record.queue_time_ms" Long
          }
        }

        consumer = span(2)
      }
      trace(1, 2) {
        span(0) {
          name "testTopic process"
          kind CONSUMER
          hasLink consumer
          status ERROR
          errorEvent IllegalArgumentException, "boom"
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" "testTopic"
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
          }
        }
        span(1) {
          name "consumer"
          childOf span(0)
        }
      }
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class ConsumerConfig {

    @Bean
    NewTopic topic() {
      return TopicBuilder.name("testTopic")
        .partitions(1)
        .replicas(1)
        .build()
    }

    @KafkaListener(id = "testListener", topics = "testTopic", containerFactory = "batchFactory")
    void listener(List<ConsumerRecord<String, String>> records, Acknowledgment acknowledgment) {
      runInternalSpan("consumer")
      acknowledgment.acknowledge()
      records.forEach({ record ->
        if (record.value() == "error") {
          throw new IllegalArgumentException("boom")
        }
      })
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> batchFactory(
      ConsumerFactory<String, String> consumerFactory) {
      ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>()
      // immediate manual acks/commits should prevent retries in the error scenario
      factory.containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE)
      factory.setConsumerFactory(consumerFactory)
      factory.setBatchListener(true)
      factory.setAutoStartup(true)
      // setting interceptBeforeTx to true eliminates kafka-clients noise - otherwise spans would be created on every ConsumerRecords#iterator() call
      factory.setContainerCustomizer({ container ->
        container.setInterceptBeforeTx(true)
      })
      factory
    }
  }
}
