/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.instrumentation.testing.junit.message.MessageHeaderUtil.headerAttributeKey;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.assertNoMetrics;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.assertProcessMetrics;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.messagingAttributes;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;

abstract class AbstractSpringIntegrationTracingTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final InstrumentationExtension testing;

  private final Class<?> additionalContextClass;

  private ConfigurableApplicationContext applicationContext;

  AbstractSpringIntegrationTracingTest(
      InstrumentationExtension testing, Class<?> additionalContextClass) {
    this.testing = testing;
    this.additionalContextClass = additionalContextClass;
  }

  @BeforeEach
  void setUp() {
    List<Class<?>> contextClasses = new ArrayList<>();
    contextClasses.add(MessageChannelsConfig.class);
    if (additionalContextClass != null) {
      contextClasses.add(additionalContextClass);
    }
    SpringApplication springApplication =
        new SpringApplication(contextClasses.toArray(new Class<?>[0]));
    springApplication.setDefaultProperties(
        singletonMap("spring.main.web-application-type", "none"));
    applicationContext = springApplication.run();
    cleanup.deferCleanup(applicationContext);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"directChannel,application.directChannel", "executorChannel,executorChannel"},
      delimiter = ',')
  void shouldPropagateContext(String channelName, String destinationName) {
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
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "process " + destinationName
                              : destinationName + " process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasAttributesSatisfyingExactly(
                          messagingAttributes("process", destinationName));
                  verifyCorrectSpanWasPropagated(capturedMessage, trace.getSpan(0));
                },
                span -> span.hasName("handler").hasParent(trace.getSpan(0))));

    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, destinationName, false);
    } else {
      assertNoMetrics(testing);
    }

    channel.unsubscribe(messageHandler);
  }

  @Test
  void shouldRecordFailedProcessMetrics() {
    assumeTrue(emitStableMessagingSemconv());

    SubscribableChannel channel =
        applicationContext.getBean("directChannel", SubscribableChannel.class);
    channel.subscribe(
        message -> {
          throw new IllegalStateException("test");
        });

    assertThatThrownBy(() -> channel.send(MessageBuilder.withPayload("test").build()))
        .isInstanceOf(RuntimeException.class);

    assertProcessMetrics(testing, "application.directChannel", true);
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
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "process application.directChannel2"
                              : "application.directChannel2 process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasAttributesSatisfyingExactly(
                          messagingAttributes("process", "application.directChannel2"));
                  verifyCorrectSpanWasPropagated(capturedMessage, trace.getSpan(0));
                },
                span -> span.hasName("handler").hasParent(trace.getSpan(0))));

    channel.unsubscribe(messageHandler);
  }

  @Test
  void shouldCreateProcessSpanWhenThereIsAnUnrelatedSpanInTheContext() {
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
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process application.directChannel"
                                : "application.directChannel process")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            messagingAttributes("process", "application.directChannel")),
                span -> span.hasName("handler").hasParent(trace.getSpan(1))));

    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "application.directChannel", false);
    } else {
      assertNoMetrics(testing);
    }

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
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "process application.linkedChannel1"
                              : "application.linkedChannel1 process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasAttributesSatisfyingExactly(
                          messagingAttributes("process", "application.linkedChannel1"));
                  verifyCorrectSpanWasPropagated(capturedMessage, trace.getSpan(1));
                },
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process application.linkedChannel2"
                                : "application.linkedChannel2 process")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            messagingAttributes("process", "application.linkedChannel2")),
                span -> span.hasName("handler").hasParent(trace.getSpan(1))));

    channel2.unsubscribe(messageHandler);
  }

  @Test
  void shouldTraceNestedDispatchOnTheSameChannel() {
    SubscribableChannel channel =
        applicationContext.getBean("directChannel", SubscribableChannel.class);

    MessageHandler messageHandler =
        message -> {
          if (!message.getHeaders().containsKey("nested")) {
            channel.send(MessageBuilder.fromMessage(message).setHeader("nested", true).build());
          } else {
            runWithSpan("handler", () -> {});
          }
        };
    channel.subscribe(messageHandler);

    channel.send(MessageBuilder.withPayload("test").build());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process application.directChannel"
                                : "application.directChannel process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            messagingAttributes("process", "application.directChannel")),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process application.directChannel"
                                : "application.directChannel process")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            messagingAttributes("process", "application.directChannel")),
                span -> span.hasName("handler").hasParent(trace.getSpan(1))));

    channel.unsubscribe(messageHandler);
  }

  @Test
  void shouldBalanceDuplicateCallbacksForNestedSameChannelDispatch() {
    SubscribableChannel channel =
        applicationContext.getBean("directChannel1", SubscribableChannel.class);

    MessageHandler messageHandler =
        message -> {
          if (!message.getHeaders().containsKey("nested")) {
            channel.send(MessageBuilder.fromMessage(message).setHeader("nested", true).build());
            runWithSpan("outerAfterNested", () -> {});
          } else {
            runWithSpan("nestedHandler", () -> {});
          }
        };
    channel.subscribe(messageHandler);

    Context before = Context.current();
    channel.send(MessageBuilder.withPayload("test").build());
    assertThat(Context.current()).isSameAs(before);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process application.directChannel2"
                                : "application.directChannel2 process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            messagingAttributes("process", "application.directChannel2")),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process application.directChannel2"
                                : "application.directChannel2 process")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            messagingAttributes("process", "application.directChannel2")),
                span -> span.hasName("nestedHandler").hasParent(trace.getSpan(1)),
                span -> span.hasName("outerAfterNested").hasParent(trace.getSpan(0))));

    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "application.directChannel2", false, 2);
    } else {
      assertNoMetrics(testing);
    }

    channel.unsubscribe(messageHandler);
  }

  @Test
  void shouldTraceNestedDispatchOfSameMessageWithDuplicateInterceptors() {
    ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel(Runnable::run);
    channel.setBeanName("duplicateExecutorChannel");
    ChannelInterceptor interceptor =
        applicationContext.getBean(GlobalChannelInterceptorWrapper.class).getChannelInterceptor();
    channel.addInterceptor(interceptor);
    channel.addInterceptor(interceptor);

    AtomicBoolean nested = new AtomicBoolean();
    MessageHandler messageHandler =
        message -> {
          if (nested.compareAndSet(false, true)) {
            channel.send(message);
            runWithSpan("outerAfterNested", () -> {});
          } else {
            runWithSpan("nestedHandler", () -> {});
          }
        };
    channel.subscribe(messageHandler);

    Context before = Context.current();
    channel.send(MessageBuilder.withPayload("test").build());
    assertThat(Context.current()).isSameAs(before);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process duplicateExecutorChannel"
                                : "duplicateExecutorChannel process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            messagingAttributes("process", "duplicateExecutorChannel")),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process duplicateExecutorChannel"
                                : "duplicateExecutorChannel process")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            messagingAttributes("process", "duplicateExecutorChannel")),
                span -> span.hasName("nestedHandler").hasParent(trace.getSpan(1)),
                span -> span.hasName("outerAfterNested").hasParent(trace.getSpan(0))));

    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "duplicateExecutorChannel", false, 2);
    } else {
      assertNoMetrics(testing);
    }

    channel.unsubscribe(messageHandler);
  }

  @Test
  void shouldTraceEachExecutorChannelHandler() throws InterruptedException {
    SubscribableChannel channel =
        applicationContext.getBean("executorChannel", SubscribableChannel.class);
    CountDownLatch handled = new CountDownLatch(2);
    MessageHandler firstHandler =
        message ->
            runWithSpan(
                "firstHandler",
                () -> {
                  handled.countDown();
                });
    MessageHandler secondHandler =
        message ->
            runWithSpan(
                "secondHandler",
                () -> {
                  handled.countDown();
                });
    channel.subscribe(firstHandler);
    channel.subscribe(secondHandler);

    testing.runWithSpan("parent", () -> channel.send(MessageBuilder.withPayload("test").build()));

    assertThat(handled.await(10, SECONDS)).isTrue();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process executorChannel"
                                : "executorChannel process")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            messagingAttributes("process", "executorChannel")),
                span -> span.hasName("firstHandler").hasParent(trace.getSpan(1)),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process executorChannel"
                                : "executorChannel process")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            messagingAttributes("process", "executorChannel")),
                span -> span.hasName("secondHandler").hasParent(trace.getSpan(3))));

    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "executorChannel", false, 2);
    } else {
      assertNoMetrics(testing);
    }

    channel.unsubscribe(firstHandler);
    channel.unsubscribe(secondHandler);
  }

  @Test
  void captureMessageHeader() {
    SubscribableChannel channel =
        applicationContext.getBean("directChannel", SubscribableChannel.class);

    CapturingMessageHandler messageHandler = new CapturingMessageHandler();
    channel.subscribe(messageHandler);

    channel.send(
        MessageBuilder.withPayload("test")
            .setHeader("Test-Message-Header", "test")
            .setHeader("Uncaptured-Header", "password")
            .build());

    Message<?> capturedMessage = messageHandler.join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "process application.directChannel"
                              : "application.directChannel process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasAttributesSatisfyingExactly(
                          messagingAttributes(
                              "process",
                              "application.directChannel",
                              equalTo(
                                  headerAttributeKey("Test-Message-Header"),
                                  singletonList("test"))));
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

    private final SubscribableChannel problematicSharedChannel = new DirectChannel();

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

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService executorChannelExecutor() {
      return Executors.newSingleThreadExecutor();
    }

    @Bean
    public SubscribableChannel executorChannel(GlobalChannelInterceptorWrapper otelInterceptor) {
      ExecutorSubscribableChannel channel =
          new ExecutorSubscribableChannel(executorChannelExecutor());
      if (!testLatestDeps()) {
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
