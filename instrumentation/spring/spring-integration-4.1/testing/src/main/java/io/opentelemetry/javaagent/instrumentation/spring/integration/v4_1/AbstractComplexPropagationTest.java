/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.SubscribableChannel;

public abstract class AbstractComplexPropagationTest {

  private final Class<?> additionalContextClass;
  protected InstrumentationExtension testing;

  ConfigurableApplicationContext applicationContext;

  public AbstractComplexPropagationTest(
      InstrumentationExtension testing, @Nullable Class<?> additionalContextClass) {
    this.testing = testing;
    this.additionalContextClass = additionalContextClass;
  }

  @BeforeEach
  void setUp() {
    List<Class<?>> contextClasses = new ArrayList<>();
    contextClasses.add(ExternalQueueConfig.class);
    if (additionalContextClass != null) {
      contextClasses.add(additionalContextClass);
    }
    SpringApplication springApplication =
        new SpringApplication(contextClasses.toArray(new Class<?>[0]));
    springApplication.setDefaultProperties(
        Collections.singletonMap("spring.main.web-application-type", "none"));
    applicationContext = springApplication.run();
  }

  @AfterEach
  void tearDown() {
    if (applicationContext != null) {
      applicationContext.close();
    }
  }

  @Test
  void shouldPropagateContextThroughAcomplexFlow() {
    SubscribableChannel sendChannel =
        applicationContext.getBean("sendChannel", SubscribableChannel.class);
    SubscribableChannel receiveChannel =
        applicationContext.getBean("receiveChannel", SubscribableChannel.class);

    CapturingMessageHandler messageHandler = new CapturingMessageHandler();
    receiveChannel.subscribe(messageHandler);

    sendChannel.send(MessageBuilder.withPayload("test").setHeader("theAnswer", "42").build());

    messageHandler.join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("application.sendChannel process").hasKind(SpanKind.CONSUMER),
                span ->
                    span.hasName("application.receiveChannel process")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.CONSUMER),
                span -> span.hasName("handler").hasParent(trace.getSpan(1))));

    receiveChannel.unsubscribe(messageHandler);
  }

  // this setup simulates separate producer/consumer and some "external" message queue in between
  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class ExternalQueueConfig {
    @Bean
    SubscribableChannel sendChannel() {
      return new ExecutorChannel(Executors.newSingleThreadExecutor());
    }

    @Bean
    SubscribableChannel receiveChannel() {
      return new DirectChannel();
    }

    @Bean
    BlockingQueue<Payload> externalQueue() {
      return new LinkedBlockingQueue<>();
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService consumerThread() {
      return Executors.newSingleThreadExecutor();
    }

    @EventListener(ApplicationReadyEvent.class)
    void initialize() {
      sendChannel().subscribe(message -> externalQueue().offer(Payload.from(message)));

      consumerThread()
          .execute(
              () -> {
                while (!Thread.interrupted()) {
                  try {
                    Payload payload = externalQueue().take();
                    receiveChannel().send(payload.toMessage());
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }
              });
    }
  }

  static class Payload {
    String body;
    Map<String, String> headers;

    Payload(String body, Map<String, String> headers) {
      this.body = body;
      this.headers = headers;
    }

    static Payload from(Message<?> message) {
      String body = (String) message.getPayload();
      Map<String, String> headers =
          message.getHeaders().entrySet().stream()
              .filter(kv -> kv.getValue() instanceof String)
              .collect(Collectors.toMap(Map.Entry::getKey, kv -> (String) kv.getValue()));
      return new Payload(body, headers);
    }

    Message<String> toMessage() {
      return MessageBuilder.withPayload(body).copyHeaders(headers).build();
    }
  }
}
