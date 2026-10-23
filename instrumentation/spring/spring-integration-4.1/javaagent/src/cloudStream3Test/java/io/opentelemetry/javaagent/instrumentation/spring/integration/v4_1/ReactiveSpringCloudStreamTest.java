/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

class ReactiveSpringCloudStreamTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void shouldPropagateEachRequestThroughReactiveSupplier() throws Exception {
    try (GenericContainer<?> rabbit =
        new GenericContainer<>("rabbitmq:4.2")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2))) {
      rabbit.start();
      SpringApplication app = new SpringApplication(Config.class);
      try (ConfigurableApplicationContext context =
          app.run(
              "--spring.main.web-application-type=none",
              "--spring.jmx.enabled=false",
              "--spring.application.name=reactiveOrders",
              "--spring.rabbitmq.host=" + rabbit.getHost(),
              "--spring.rabbitmq.port=" + rabbit.getMappedPort(5672),
              "--spring.cloud.function.definition=orders;consume",
              "--spring.cloud.stream.bindings.orders-out-0.destination=orders",
              "--spring.cloud.stream.bindings.consume-in-0.destination=orders",
              "--spring.cloud.stream.bindings.consume-in-0.group=regression")) {
        Config config = context.getBean(Config.class);
        // Regression for
        // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/3096:
        // the supplier is subscribed at startup, outside either request's context.
        assertThat(config.orders.currentSubscriberCount()).isEqualTo(1);
        int port = PortUtils.findOpenPort();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext(
            "/orders",
            exchange -> {
              String id = exchange.getRequestURI().getQuery();
              Span request =
                  testing
                      .getOpenTelemetry()
                      .getTracer("test")
                      .spanBuilder("request/" + id)
                      .setSpanKind(SpanKind.SERVER)
                      .startSpan();
              try (Scope ignored = request.makeCurrent()) {
                config.orders.tryEmitNext(new Order(id)).orThrow();
                exchange.sendResponseHeaders(202, -1);
              } finally {
                exchange.close();
                request.end();
              }
            });
        server.start();
        try {
          for (String id : new String[] {"first", "second"}) {
            HttpURLConnection connection =
                (HttpURLConnection)
                    new URL("http://localhost:" + port + "/orders?" + id).openConnection();
            try {
              connection.setRequestMethod("POST");
              connection.setConnectTimeout(10_000);
              connection.setReadTimeout(10_000);
              assertThat(connection.getResponseCode()).isEqualTo(202);
              assertThat(config.received.poll(10, SECONDS)).isEqualTo(id);
            } finally {
              connection.disconnect();
            }
          }
        } finally {
          server.stop(0);
        }

        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("request/first").hasKind(SpanKind.SERVER).hasNoParent(),
                    span ->
                        span.hasName("process reactiveOrders.orders_integrationflow.channel#0")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("process reactiveOrders.consume-in-0")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1)),
                    span -> span.hasName("consume/first").hasParent(trace.getSpan(2))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("request/second").hasKind(SpanKind.SERVER).hasNoParent(),
                    span ->
                        span.hasName("process reactiveOrders.orders_integrationflow.channel#0")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("process reactiveOrders.consume-in-0")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1)),
                    span -> span.hasName("consume/second").hasParent(trace.getSpan(2))));
      }
    }
  }

  @Test
  void shouldKeepQueuedMessagesInTheirEmitterContexts() throws InterruptedException {
    Span firstParent =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("first").startSpan();
    Span secondParent =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("second").startSpan();
    SpanContext ambientContext = Span.current().getSpanContext();
    FluxMessageChannel channel = new FluxMessageChannel();
    Sinks.Many<Message<?>> source = Sinks.many().multicast().onBackpressureBuffer();
    CountDownLatch sourceSubscribed = new CountDownLatch(1);
    CountDownLatch schedulerBlocked = new CountDownLatch(1);
    CountDownLatch resumeScheduler = new CountDownLatch(1);
    BlockingQueue<Delivery> delivered = new LinkedBlockingQueue<>();
    AtomicReference<Throwable> subscriberError = new AtomicReference<>();
    Disposable schedulerGate =
        Schedulers.boundedElastic()
            .schedule(
                () -> {
                  schedulerBlocked.countDown();
                  try {
                    resumeScheduler.await(30, SECONDS);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                });

    try {
      assertThat(secondParent.getSpanContext()).isNotEqualTo(firstParent.getSpanContext());
      channel.setBeanName("queuedReactive");
      channel.subscribeTo(source.asFlux().doOnSubscribe(ignored -> sourceSubscribed.countDown()));
      channel.subscribe(
          new Subscriber<Message<?>>() {
            @Override
            public void onSubscribe(Subscription subscription) {
              subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Message<?> message) {
              Order order = (Order) message.getPayload();
              delivered.add(new Delivery(order.id, Span.current().getSpanContext()));
            }

            @Override
            public void onError(Throwable error) {
              subscriberError.set(error);
            }

            @Override
            public void onComplete() {}
          });

      assertThat(schedulerBlocked.await(10, SECONDS)).isTrue();
      assertThat(sourceSubscribed.await(10, SECONDS)).isTrue();
      assertThat(source.currentSubscriberCount()).isEqualTo(1);

      try (Scope ignored = firstParent.makeCurrent()) {
        source.tryEmitNext(MessageBuilder.withPayload(new Order("first")).build()).orThrow();
        assertThat(Span.current().getSpanContext()).isEqualTo(firstParent.getSpanContext());
      }
      assertThat(Span.current().getSpanContext()).isEqualTo(ambientContext);

      try (Scope ignored = secondParent.makeCurrent()) {
        source.tryEmitNext(MessageBuilder.withPayload(new Order("second")).build()).orThrow();
        assertThat(Span.current().getSpanContext()).isEqualTo(secondParent.getSpanContext());
      }
      assertThat(Span.current().getSpanContext()).isEqualTo(ambientContext);
      assertThat(delivered).isEmpty();

      resumeScheduler.countDown();
      Delivery first = delivered.poll(10, SECONDS);
      Delivery second = delivered.poll(10, SECONDS);
      assertThat(first).isNotNull();
      assertThat(second).isNotNull();
      assertThat(subscriberError.get()).isNull();
      assertThat(asList(first.id, second.id)).containsExactly("first", "second");
      assertThat(asList(first.context, second.context))
          .containsExactly(firstParent.getSpanContext(), secondParent.getSpanContext());
    } finally {
      resumeScheduler.countDown();
      schedulerGate.dispose();
      try {
        channel.destroy();
      } finally {
        firstParent.end();
        secondParent.end();
      }
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class Config {
    final Sinks.Many<Order> orders = Sinks.many().multicast().onBackpressureBuffer();
    final BlockingQueue<String> received = new LinkedBlockingQueue<>();

    @Bean
    Supplier<Flux<Order>> orders() {
      return orders::asFlux;
    }

    @Bean
    Consumer<Order> consume() {
      return order -> testing.runWithSpan("consume/" + order.id, () -> received.add(order.id));
    }
  }

  static class Order {
    public String id;

    public Order() {}

    Order(String id) {
      this.id = id;
    }
  }

  static class Delivery {
    final String id;
    final SpanContext context;

    Delivery(String id, SpanContext context) {
      this.id = id;
      this.context = context;
    }
  }
}
