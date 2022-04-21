/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.kafka.core.KafkaTemplate
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.wait.strategy.Wait
import spock.lang.Shared

import java.time.Duration

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.api.trace.StatusCode.ERROR

class SpringKafkaInstrumentationTest extends AgentInstrumentationSpecification {
  @Shared
  static KafkaContainer kafka
  @Shared
  static ConfigurableApplicationContext applicationContext

  def setupSpec() {
    kafka = new KafkaContainer()
      .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.KafkaServer\\).*", 1))
      .withStartupTimeout(Duration.ofMinutes(1))
    kafka.start()

    def app = new SpringApplication(ConsumerConfig)
    app.setDefaultProperties([
      "spring.jmx.enabled"                         : false,
      "spring.main.web-application-type"           : "none",
      "spring.kafka.bootstrap-servers"             : kafka.bootstrapServers,
      "spring.kafka.consumer.auto-offset-reset"    : "earliest",
      "spring.kafka.consumer.linger-ms"            : 10,
      // wait 1s between poll() calls
      "spring.kafka.listener.idle-between-polls"   : 1000,
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
    // This test assumes that messages are sent and received as a batch. Occasionally it happens
    // that the messages are not received as a batch, but one by one. This doesn't match what the
    // assertion expects. To reduce flakiness we retry the test when messages weren't received as
    // a batch.
    def maxAttempts = 5
    for (i in 1..maxAttempts) {
      BatchRecordListener.reset()

      runWithSpan("producer") {
        kafkaTemplate.executeInTransaction({ ops ->
          ops.send("testBatchTopic", "10", "testSpan1")
          ops.send("testBatchTopic", "20", "testSpan2")
        })
      }

      BatchRecordListener.waitForMessages()
      if (BatchRecordListener.getLastBatchSize() == 2) {
        break
      } else if (i < maxAttempts) {
        ignoreTracesAndClear(2)
        System.err.println("Messages weren't received as batch, retrying")
      }
    }

    then:
    assertTraces(2) {
      traces.sort(orderByRootSpanName("producer", "testBatchTopic receive", "testBatchTopic process"))

      SpanData producer1, producer2

      trace(0, 3) {
        span(0) {
          name "producer"
        }
        span(1) {
          name "testBatchTopic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testBatchTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
          }
        }
        span(2) {
          name "testBatchTopic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testBatchTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
          }
        }

        producer1 = span(1)
        producer2 = span(2)
      }
      trace(1, 3) {
        span(0) {
          name "testBatchTopic receive"
          kind CONSUMER
          hasNoParent()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testBatchTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
          }
        }
        span(1) {
          name "testBatchTopic process"
          kind CONSUMER
          childOf span(0)
          hasLink producer1
          hasLink producer2
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testBatchTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
          }
        }
        span(2) {
          name "consumer"
          childOf span(1)
        }
      }
    }
  }

  def "should handle failure in Kafka batch listener"() {
    given:
    def kafkaTemplate = applicationContext.getBean("kafkaTemplate", KafkaTemplate)

    when:
    runWithSpan("producer") {
      kafkaTemplate.executeInTransaction({ ops ->
        ops.send("testBatchTopic", "10", "error")
      })
    }

    then:
    assertTraces(2) {
      traces.sort(orderByRootSpanName("producer", "testBatchTopic receive"))

      SpanData producer

      trace(0, 2) {
        span(0) {
          name "producer"
        }
        span(1) {
          name "testBatchTopic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testBatchTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
          }
        }

        producer = span(1)
      }
      trace(1, 3) {
        span(0) {
          name "testBatchTopic receive"
          kind CONSUMER
          hasNoParent()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testBatchTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
          }
        }
        span(1) {
          name "testBatchTopic process"
          kind CONSUMER
          childOf span(0)
          hasLink producer
          status ERROR
          errorEvent IllegalArgumentException, "boom"
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testBatchTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
          }
        }
        span(2) {
          name "consumer"
          childOf span(1)
        }
      }
    }
  }

  def "should create spans for single record receive+process"() {
    given:
    def kafkaTemplate = applicationContext.getBean("kafkaTemplate", KafkaTemplate)

    when:
    runWithSpan("producer") {
      kafkaTemplate.executeInTransaction({ ops ->
        ops.send("testSingleTopic", "10", "testSpan")
      })
    }

    then:
    assertTraces(2) {
      traces.sort(orderByRootSpanName("producer", "testSingleTopic receive"))

      SpanData producer

      trace(0, 2) {
        span(0) {
          name "producer"
        }
        span(1) {
          name "testSingleTopic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testSingleTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
          }
        }

        producer = span(1)
      }
      trace(1, 3) {
        span(0) {
          name "testSingleTopic receive"
          kind CONSUMER
          hasNoParent()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testSingleTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
          }
        }
        span(1) {
          name "testSingleTopic process"
          kind CONSUMER
          childOf span(0)
          hasLink producer
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testSingleTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_KAFKA_PARTITION" { it >= 0 }
            "kafka.offset" Long
            "kafka.record.queue_time_ms" { it >= 0 }
          }
        }
        span(2) {
          name "consumer"
          childOf span(1)
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
        ops.send("testSingleTopic", "10", "error")
      })
    }

    then:
    assertTraces(2) {
      traces.sort(orderByRootSpanName("producer", "testSingleTopic receive"))

      SpanData producer

      trace(0, 2) {
        span(0) {
          name "producer"
        }
        span(1) {
          name "testSingleTopic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testSingleTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
          }
        }

        producer = span(1)
      }
      trace(1, 3) {
        span(0) {
          name "testSingleTopic receive"
          kind CONSUMER
          hasNoParent()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testSingleTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
          }
        }
        span(1) {
          name "testSingleTopic process"
          kind CONSUMER
          childOf span(0)
          hasLink producer
          status ERROR
          errorEvent IllegalArgumentException, "boom"
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testSingleTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_KAFKA_PARTITION" { it >= 0 }
            "kafka.offset" Long
            "kafka.record.queue_time_ms" { it >= 0 }
          }
        }
        span(2) {
          name "consumer"
          childOf span(1)
        }
      }
    }
  }
}
