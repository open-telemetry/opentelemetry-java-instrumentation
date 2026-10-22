/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.assertProcessMetrics;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.messagingAttributes;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.interceptor.GlobalChannelInterceptorWrapper;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;

abstract class AbstractSpringIntegration41Test {

  private final InstrumentationExtension testing;
  private AnnotationConfigApplicationContext applicationContext;

  AbstractSpringIntegration41Test(InstrumentationExtension testing) {
    this.testing = testing;
  }

  void configure(AnnotationConfigApplicationContext context) {}

  @BeforeEach
  void setUp() {
    applicationContext = new AnnotationConfigApplicationContext();
    applicationContext.setId("application");
    applicationContext.register(Channels.class);
    configure(applicationContext);
    applicationContext.refresh();
  }

  @AfterEach
  void tearDown() {
    applicationContext.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"directChannel", "integrationExecutorChannel"})
  void shouldRegisterGlobalInterceptorAndPropagateContext(String channelName) {
    SubscribableChannel channel =
        applicationContext.getBean(channelName, SubscribableChannel.class);
    AtomicReference<Message<?>> captured = new AtomicReference<>();
    channel.subscribe(
        message -> {
          captured.set(message);
          testing.runWithSpan("handler", () -> {});
        });

    Context before = Context.current();
    testing.runWithSpan("parent", () -> channel.send(MessageBuilder.withPayload("test").build()));
    assertThat(Context.current()).isSameAs(before);

    String destination = "application." + channelName;
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> {
                  span.hasName("process " + destination)
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(messagingAttributes("process", destination));
                  assertPropagation(captured.get(), trace.getSpan(1));
                },
                span -> span.hasName("handler").hasParent(trace.getSpan(1))));
    assertProcessMetrics(testing, destination, false);
  }

  @Test
  void shouldRestoreOuterContextAfterNestedSend() {
    DirectChannel outer = applicationContext.getBean("directChannel", DirectChannel.class);
    DirectChannel inner = applicationContext.getBean("nestedChannel", DirectChannel.class);
    inner.subscribe(message -> testing.runWithSpan("innerHandler", () -> {}));
    outer.subscribe(
        message -> {
          Context before = Context.current();
          inner.send(message);
          assertThat(Context.current()).isSameAs(before);
          testing.runWithSpan("outerHandler", () -> {});
        });

    Context before = Context.current();
    outer.send(MessageBuilder.withPayload("test").build());
    assertThat(Context.current()).isSameAs(before);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("process application.directChannel").hasKind(SpanKind.CONSUMER),
                span ->
                    span.hasName("process application.nestedChannel")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0)),
                span -> span.hasName("innerHandler").hasParent(trace.getSpan(1)),
                span -> span.hasName("outerHandler").hasParent(trace.getSpan(0))));
    assertProcessMetrics(testing, "application.directChannel", false);
    assertProcessMetrics(testing, "application.nestedChannel", false);
  }

  @Test
  void shouldCleanUpAfterFailedSend() {
    DirectChannel channel = applicationContext.getBean("directChannel", DirectChannel.class);
    channel.subscribe(
        message -> {
          throw new IllegalStateException("failed handler");
        });

    Context before = Context.current();
    Throwable failure =
        catchThrowable(() -> channel.send(MessageBuilder.withPayload("test").build()));
    assertThat(failure)
        .isInstanceOf(MessageDeliveryException.class)
        .hasRootCauseMessage("failed handler");
    assertThat(Context.current()).isSameAs(before);
    testing.runWithSpan("afterFailure", () -> {});

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("process application.directChannel")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasException(failure)),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("afterFailure").hasNoParent()));
    assertProcessMetrics(testing, "application.directChannel", true);
  }

  @Test
  void shouldUseSendTimeFallbackForIntegrationExecutorChannel() throws Exception {
    LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    ExecutorChannel channel = new ExecutorChannel(tasks::add);
    channel.setBeanName("deferredChannel");
    channel.setBeanFactory(applicationContext.getBeanFactory());
    channel.addInterceptor(interceptor());
    channel.afterPropertiesSet();
    AtomicReference<Message<?>> captured = new AtomicReference<>();
    channel.subscribe(captured::set);

    Context before = Context.current();
    testing.runWithSpan("parent", () -> channel.send(MessageBuilder.withPayload("test").build()));
    assertThat(Context.current()).isSameAs(before);

    // Integration 4.1 has no ExecutorChannelInterceptor callbacks: the consumer ends at send,
    // before the queued handler runs. Its context is still propagated in the message headers.
    AtomicReference<SpanData> consumer = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> {
                  span.hasName("process application.deferredChannel")
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(0));
                  consumer.set(trace.getSpan(1));
                }));
    assertThat(captured.get()).isNull();
    Runnable task = tasks.poll(10, SECONDS);
    assertThat(task).isNotNull();
    task.run();
    assertThat(Context.current()).isSameAs(before);
    assertPropagation(captured.get(), consumer.get());
    assertProcessMetrics(testing, "application.deferredChannel", false);
  }

  @Test
  void shouldTraceFrameworkExecutorHandler() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel(executor);
      channel.setBeanName("frameworkExecutorChannel");
      // The 4.1 global processor only discovers Integration ChannelInterceptorAware beans.
      channel.addInterceptor(interceptor());
      CompletableFuture<Message<?>> captured = new CompletableFuture<>();
      channel.subscribe(
          message -> {
            testing.runWithSpan("handler", () -> {});
            captured.complete(message);
          });

      Context before = Context.current();
      testing.runWithSpan("parent", () -> channel.send(MessageBuilder.withPayload("test").build()));
      Message<?> message = captured.get(10, SECONDS);
      assertThat(Context.current()).isSameAs(before);
      // Also wait for afterMessageHandled before probing the reused worker's context.
      assertThat(executor.submit(() -> Span.current().getSpanContext().isValid()).get(10, SECONDS))
          .isFalse();

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span -> {
                    span.hasName("process frameworkExecutorChannel")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            messagingAttributes("process", "frameworkExecutorChannel"));
                    assertPropagation(message, trace.getSpan(1));
                  },
                  span -> span.hasName("handler").hasParent(trace.getSpan(1))));
      assertProcessMetrics(testing, "frameworkExecutorChannel", false);
    } finally {
      executor.shutdownNow();
    }
  }

  private ChannelInterceptor interceptor() {
    return applicationContext
        .getBean(GlobalChannelInterceptorWrapper.class)
        .getChannelInterceptor();
  }

  private static void assertPropagation(Message<?> message, SpanData span) {
    assertThat(message.getHeaders().get("traceparent"))
        .isEqualTo("00-" + span.getTraceId() + "-" + span.getSpanId() + "-01");
  }

  @EnableIntegration
  static class Channels {
    @Bean
    DirectChannel directChannel() {
      return new DirectChannel();
    }

    @Bean
    DirectChannel nestedChannel() {
      return new DirectChannel();
    }

    @Bean
    ExecutorChannel integrationExecutorChannel() {
      return new ExecutorChannel(Runnable::run);
    }
  }
}
