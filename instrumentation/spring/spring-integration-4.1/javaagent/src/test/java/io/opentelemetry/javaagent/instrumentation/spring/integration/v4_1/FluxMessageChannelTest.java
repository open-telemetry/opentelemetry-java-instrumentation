/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

class FluxMessageChannelTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void shouldPropagateEmitterContextThroughSubscribeTo() throws Exception {
    try (AnnotationConfigApplicationContext application =
        new AnnotationConfigApplicationContext()) {
      application.setId("application");
      application.register(Channels.class);
      application.refresh();
      FluxMessageChannel channel = application.getBean(FluxMessageChannel.class);
      CompletableFuture<FluxSink<Message<?>>> subscribed = new CompletableFuture<>();
      CompletableFuture<Message<?>> delivered = new CompletableFuture<>();
      CompletableFuture<Thread> producerThread = new CompletableFuture<>();
      CompletableFuture<Thread> handlerThread = new CompletableFuture<>();
      Flux<Message<?>> source = Flux.create(subscribed::complete);
      Context before = Context.current();
      Disposable subscription =
          Flux.from(channel)
              .subscribe(
                  message -> {
                    handlerThread.complete(Thread.currentThread());
                    testing.runWithSpan("handler", () -> {});
                    delivered.complete(message);
                  },
                  delivered::completeExceptionally);
      try {
        channel.subscribeTo(source);
        FluxSink<Message<?>> emitter = subscribed.get(10, SECONDS);

        testing.runWithSpan(
            "producer",
            () -> {
              Context producer = Context.current();
              producerThread.complete(Thread.currentThread());
              emitter.next(MessageBuilder.withPayload("test").build());
              assertThat(Span.current().getSpanContext())
                  .isEqualTo(Span.fromContext(producer).getSpanContext());
            });
        assertThat(Context.current()).isSameAs(before);
        assertThat(delivered.get(10, SECONDS).getPayload()).isEqualTo("test");
        if (testLatestDeps()) {
          assertThat(handlerThread.get(10, SECONDS)).isNotSameAs(producerThread.get(10, SECONDS));
        } else {
          assertThat(handlerThread.get(10, SECONDS)).isSameAs(producerThread.get(10, SECONDS));
        }

        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("producer").hasNoParent(),
                    span ->
                        span.hasName("process application.fluxChannel")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0)),
                    span -> span.hasName("handler").hasParent(trace.getSpan(1))));
      } finally {
        subscription.dispose();
      }
    }
  }

  @EnableIntegration
  static class Channels {
    @Bean
    FluxMessageChannel fluxChannel() {
      return new FluxMessageChannel();
    }
  }
}
