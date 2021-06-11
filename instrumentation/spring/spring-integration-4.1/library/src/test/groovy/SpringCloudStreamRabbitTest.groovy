/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runInternalSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import io.opentelemetry.instrumentation.test.LibraryInstrumentationSpecification
import java.time.Duration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.cloud.stream.messaging.Sink
import org.springframework.cloud.stream.messaging.Source
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.messaging.support.MessageBuilder
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared

class SpringCloudStreamRabbitTest extends LibraryInstrumentationSpecification {

  @Shared
  def rabbitMQContainer

  @Shared
  ConfigurableApplicationContext producerContext
  @Shared
  ConfigurableApplicationContext consumerContext

  def setupSpec() {
    rabbitMQContainer = new GenericContainer('rabbitmq:latest')
      .withExposedPorts(5672)
      .withStartupTimeout(Duration.ofSeconds(120))
    rabbitMQContainer.start()

    def producerApp = new SpringApplication(ProducerConfig, GlobalInterceptorSpringConfig)
    producerApp.setDefaultProperties([
      "spring.application.name"                        : "testProducer",
      "spring.jmx.enabled"                             : false,
      "spring.main.web-application-type"               : "none",
      "spring.rabbitmq.host"                           : rabbitMQContainer.containerIpAddress,
      "spring.rabbitmq.port"                           : rabbitMQContainer.getMappedPort(5672),
      "spring.cloud.stream.bindings.output.destination": "testTopic"
    ])
    producerContext = producerApp.run()

    def consumerApp = new SpringApplication(ConsumerConfig, GlobalInterceptorSpringConfig)
    consumerApp.setDefaultProperties([
      "spring.application.name"                       : "testConsumer",
      "spring.jmx.enabled"                            : false,
      "spring.main.web-application-type"              : "none",
      "spring.rabbitmq.host"                          : rabbitMQContainer.containerIpAddress,
      "spring.rabbitmq.port"                          : rabbitMQContainer.getMappedPort(5672),
      "spring.cloud.stream.bindings.input.destination": "testTopic"
    ])
    consumerContext = consumerApp.run()
  }

  def cleanupSpec() {
    rabbitMQContainer?.stop()
    producerContext?.close()
    consumerContext?.close()
  }

  def "should propagate context through RabbitMQ"() {
    when:
    producerContext.getBean("producer", Runnable).run()

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "producer"
        }
        span(1) {
          name "testProducer.output"
          childOf span(0)
        }
        span(2) {
          name "testConsumer.input"
          childOf span(1)
        }
        span(3) {
          name "consumer"
          childOf span(2)
        }
      }
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableBinding(Source)
  static class ProducerConfig {
    @Autowired
    Source source

    @Bean
    Runnable producer() {
      return {
        runUnderTrace("producer") {
          source.output().send(MessageBuilder.withPayload("test").build())
        }
      }
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableBinding(Sink)
  static class ConsumerConfig {
    @StreamListener(Sink.INPUT)
    void consume(String ignored) {
      runInternalSpan("consumer")
    }
  }
}
