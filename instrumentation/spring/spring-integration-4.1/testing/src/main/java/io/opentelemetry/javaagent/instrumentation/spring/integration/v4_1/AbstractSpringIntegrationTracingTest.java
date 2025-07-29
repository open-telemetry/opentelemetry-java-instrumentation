/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.interceptor.GlobalChannelInterceptorWrapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;

abstract class AbstractSpringIntegrationTracingTest {

  protected final InstrumentationExtension testing;

  private final Class<?> additionalContextClass;

  ConfigurableApplicationContext applicationContext;

  public AbstractSpringIntegrationTracingTest(
      InstrumentationExtension testing, Class<?> additionalContextClass) {
    this.testing = testing;
    this.additionalContextClass = additionalContextClass;
  }

  @BeforeEach
  public void setUp() {
    List<Class<?>> contextClasses = new ArrayList<>();
    contextClasses.add(MessageChannelsConfig.class);
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
  public void tearDown() {
    if (applicationContext != null) {
      applicationContext.close();
    }
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "directChannel,application.directChannel process",
        "executorChannel,executorChannel process"
      },
      delimiter = ',')
  public void shouldPropagateContext(String channelName, String interceptorSpanName) {
    SubscribableChannel channel =
        applicationContext.getBean(channelName, SubscribableChannel.class);

    CapturingMessageHandler messageHandler = new CapturingMessageHandler();
    channel.subscribe(messageHandler);

    channel.send(MessageBuilder.withPayload("test").build());

    Message<?> capturedMessage = messageHandler.join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName(interceptorSpanName).hasKind(SpanKind.CONSUMER);
                  verifyCorrectSpanWasPropagated(capturedMessage, trace.getSpan(0));
                },
                span -> span.hasName("handler").hasParent(trace.getSpan(0))));

    channel.unsubscribe(messageHandler);
  }

  @Test
  void shouldNotAddInterceptorTwice() {
    SubscribableChannel channel =
        applicationContext.getBean("directChannel1", SubscribableChannel.class);

    CapturingMessageHandler messageHandler = new CapturingMessageHandler();
    channel.subscribe(messageHandler);

    channel.send(MessageBuilder.withPayload("test").build());

    Message<?> capturedMessage = messageHandler.join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("application.directChannel2 process").hasKind(SpanKind.CONSUMER);
                  verifyCorrectSpanWasPropagated(capturedMessage, trace.getSpan(0));
                },
                span -> span.hasName("handler").hasParent(trace.getSpan(0))));

    channel.unsubscribe(messageHandler);
  }

  @Test
  void shouldNotCreateAspanWhenThereIsAlreadyAspanInTheContext() {
    SubscribableChannel channel =
        applicationContext.getBean("directChannel", SubscribableChannel.class);

    CapturingMessageHandler messageHandler = new CapturingMessageHandler();
    channel.subscribe(messageHandler);

    testing.runWithSpan(
        "parent",
        () -> {
          channel.send(MessageBuilder.withPayload("test").build());
        });

    messageHandler.join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasName("handler").hasParent(trace.getSpan(0))));

    channel.unsubscribe(messageHandler);
  }

  @Test
  void shouldHandleMultipleMessageChannelsInAchain() {
    SubscribableChannel channel1 =
        applicationContext.getBean("linkedChannel1", SubscribableChannel.class);
    SubscribableChannel channel2 =
        applicationContext.getBean("linkedChannel2", SubscribableChannel.class);

    CapturingMessageHandler messageHandler = new CapturingMessageHandler();
    channel2.subscribe(messageHandler);

    channel1.send(MessageBuilder.withPayload("test").build());

    Message<?> capturedMessage = messageHandler.join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("application.linkedChannel1 process").hasKind(SpanKind.CONSUMER);
                  verifyCorrectSpanWasPropagated(capturedMessage, trace.getSpan(0));
                },
                span -> span.hasName("handler").hasParent(trace.getSpan(0))));

    channel2.unsubscribe(messageHandler);
  }

  @Test
  void captureMessageHeader() {
    SubscribableChannel channel =
        applicationContext.getBean("directChannel", SubscribableChannel.class);

    CapturingMessageHandler messageHandler = new CapturingMessageHandler();
    channel.subscribe(messageHandler);

    channel.send(
        MessageBuilder.withPayload("test").setHeader("test-message-header", "test").build());

    Message<?> capturedMessage = messageHandler.join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("application.directChannel process").hasKind(SpanKind.CONSUMER);
                  verifyCorrectSpanWasPropagated(capturedMessage, trace.getSpan(0));
                },
                span -> span.hasName("handler").hasParent(trace.getSpan(0))));

    channel.unsubscribe(messageHandler);
  }

  static void verifyCorrectSpanWasPropagated(Message<?> capturedMessage, SpanData parentSpan) {
    String propagatedSpan = (String) capturedMessage.getHeaders().get("traceparent");
    assertThat(propagatedSpan).contains(parentSpan.getTraceId());
    assertThat(propagatedSpan).contains(parentSpan.getSpanId());
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  public static class MessageChannelsConfig {

    SubscribableChannel problematicSharedChannel = new DirectChannel();

    @Bean
    public SubscribableChannel directChannel() {
      return new DirectChannel();
    }

    @Bean
    public SubscribableChannel directChannel1() {
      return problematicSharedChannel;
    }

    @Bean
    public SubscribableChannel directChannel2() {
      return problematicSharedChannel;
    }

    @Bean
    public SubscribableChannel executorChannel(GlobalChannelInterceptorWrapper otelInterceptor) {
      ExecutorSubscribableChannel channel =
          new ExecutorSubscribableChannel(Executors.newSingleThreadExecutor());
      if (!Boolean.getBoolean("testLatestDeps")) {
        // spring does not inject the interceptor in 4.1 because ExecutorSubscribableChannel isn't
        // ChannelInterceptorAware
        // in later versions spring injects the global interceptor into InterceptableChannel (which
        // ExecutorSubscribableChannel is)
        channel.addInterceptor(otelInterceptor.getChannelInterceptor());
      }
      return channel;
    }

    @Bean
    public SubscribableChannel linkedChannel1() {
      return new DirectChannel();
    }

    @Bean
    public SubscribableChannel linkedChannel2() {
      return new DirectChannel();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
      linkedChannel1().subscribe(message -> linkedChannel2().send(message));
    }
  }
}
