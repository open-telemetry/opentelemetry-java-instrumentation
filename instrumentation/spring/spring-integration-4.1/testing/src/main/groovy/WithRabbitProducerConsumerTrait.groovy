/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


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

import java.time.Duration

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runInternalSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

trait WithRabbitProducerConsumerTrait {

  static GenericContainer rabbitMqContainer
  static ConfigurableApplicationContext producerContext
  static ConfigurableApplicationContext consumerContext

  def startRabbit(Class<?> additionalContext = null) {
    rabbitMqContainer = new GenericContainer('rabbitmq:latest')
      .withExposedPorts(5672)
      .withStartupTimeout(Duration.ofSeconds(120))
    rabbitMqContainer.start()

    def producerApp = new SpringApplication(getContextClasses(ProducerConfig, additionalContext))
    producerApp.setDefaultProperties([
      "spring.application.name"                        : "testProducer",
      "spring.jmx.enabled"                             : false,
      "spring.main.web-application-type"               : "none",
      "spring.rabbitmq.host"                           : rabbitMqContainer.containerIpAddress,
      "spring.rabbitmq.port"                           : rabbitMqContainer.getMappedPort(5672),
      "spring.cloud.stream.bindings.output.destination": "testTopic"
    ])
    producerContext = producerApp.run()

    def consumerApp = new SpringApplication(getContextClasses(ConsumerConfig, additionalContext))
    consumerApp.setDefaultProperties([
      "spring.application.name"                       : "testConsumer",
      "spring.jmx.enabled"                            : false,
      "spring.main.web-application-type"              : "none",
      "spring.rabbitmq.host"                          : rabbitMqContainer.containerIpAddress,
      "spring.rabbitmq.port"                          : rabbitMqContainer.getMappedPort(5672),
      "spring.cloud.stream.bindings.input.destination": "testTopic"
    ])
    consumerContext = consumerApp.run()
  }

  private Class<?>[] getContextClasses(Class<?> mainContext, Class<?> additionalContext) {
    def contextClasses = [mainContext]
    if (additionalContext != null) {
      contextClasses += additionalContext
    }
    contextClasses
  }

  def stopRabbit() {
    rabbitMqContainer?.stop()
    rabbitMqContainer = null
    producerContext?.close()
    producerContext = null
    consumerContext?.close()
    consumerContext = null
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