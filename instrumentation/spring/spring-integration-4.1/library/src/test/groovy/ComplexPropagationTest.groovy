/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.spring.integration.SpringIntegrationTracing
import io.opentelemetry.instrumentation.test.LibraryInstrumentationSpecification
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Collectors
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.event.EventListener
import org.springframework.integration.channel.DirectChannel
import org.springframework.integration.channel.ExecutorChannel
import org.springframework.integration.config.GlobalChannelInterceptor
import org.springframework.messaging.Message
import org.springframework.messaging.SubscribableChannel
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageBuilder
import spock.lang.Shared

class ComplexPropagationTest extends LibraryInstrumentationSpecification {

  @Shared
  ConfigurableApplicationContext applicationContext

  def setupSpec() {
    def app = new SpringApplication(ExternalQueueConfig)
    applicationContext = app.run()
  }

  def cleanupSpec() {
    applicationContext?.close()
  }

  def "should propagate context through a custom message queue"() {
    given:
    def sendChannel = applicationContext.getBean("sendChannel", SubscribableChannel)
    def receiveChannel = applicationContext.getBean("receiveChannel", SubscribableChannel)

    def messageHandler = new CapturingMessageHandler()
    receiveChannel.subscribe(messageHandler)

    when:
    runUnderTrace("parent") {
      sendChannel.send(MessageBuilder.withPayload("test")
        .setHeader("theAnswer", "42")
        .build())
    }

    then:
    messageHandler.join()

    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
        }
        span(1) {
          name "application.sendChannel"
          childOf span(0)
        }
        span(2) {
          name "application.receiveChannel"
          childOf span(1)
        }
        span(3) {
          name "handler"
          childOf span(2)
        }
      }
    }

    cleanup:
    receiveChannel.unsubscribe(messageHandler)
  }

  // this setup simulates separate producer/consumer and some "external" message queue in between
  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class ExternalQueueConfig {
    @Bean
    SubscribableChannel sendChannel() {
      new ExecutorChannel(Executors.newSingleThreadExecutor())
    }

    @Bean
    SubscribableChannel receiveChannel() {
      new DirectChannel()
    }

    @Bean
    BlockingQueue<Payload> externalQueue() {
      new LinkedBlockingQueue<Payload>()
    }

    @Bean
    ExecutorService consumerThread() {
      Executors.newSingleThreadExecutor()
    }

    @EventListener(ApplicationReadyEvent)
    void initialize() {
      sendChannel().subscribe { message ->
        externalQueue().offer(Payload.from(message))
      }

      consumerThread().execute({
        while (!Thread.interrupted()) {
          def payload = externalQueue().take()
          receiveChannel().send(payload.toMessage())
        }
      })
    }

    @GlobalChannelInterceptor
    @Bean
    ChannelInterceptor otelInterceptor() {
      SpringIntegrationTracing.create(GlobalOpenTelemetry.get()).newChannelInterceptor()
    }
  }

  static class Payload {
    String body
    Map<String, String> headers

    static Payload from(Message<?> message) {
      def body = message.payload as String
      Map<String, String> headers = message.headers.entrySet().stream()
        .filter({ kv -> kv.value instanceof String })
        .collect(Collectors.toMap({ it.key }, { it.value }))
      new Payload(body: body, headers: headers)
    }

    Message<String> toMessage() {
      MessageBuilder.withPayload(body)
        .copyHeaders(headers)
        .build()
    }
  }
}
