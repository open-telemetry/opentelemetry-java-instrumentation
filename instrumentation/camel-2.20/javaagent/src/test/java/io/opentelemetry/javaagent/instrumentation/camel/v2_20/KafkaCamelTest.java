/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelMessagingMetricsAssertions.assertSendAndProcessMetrics;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

class KafkaCamelTest {

  private static final String TOPIC = "camel-test";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static CamelContext camelContext;
  private static final CountDownLatch received = new CountDownLatch(1);

  @BeforeAll
  static void setUp() throws Exception {
    KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx256m")
            .withStartupTimeout(Duration.ofMinutes(1));
    kafka.start();
    cleanup.deferAfterAll(kafka::stop);

    String brokers = kafka.getBootstrapServers().replace("PLAINTEXT://", "");
    String kafkaEndpoint =
        "kafka:" + TOPIC + "?brokers=" + brokers + "&groupId=camel-test&autoOffsetReset=earliest";

    camelContext = new DefaultCamelContext();
    camelContext.addRoutes(
        new RouteBuilder() {
          @Override
          public void configure() {
            from("direct:input").toD(kafkaEndpoint);
            from(kafkaEndpoint)
                .to("direct:consume")
                .process(
                    exchange -> {
                      throw new IllegalStateException("test");
                    });
            from("direct:consume").process(exchange -> received.countDown());
          }
        });
    camelContext.start();
    cleanup.deferAfterAll(camelContext::stop);
  }

  @Test
  void camelOwnsMetricsOverKafkaClients() throws Exception {
    ProducerTemplate template = camelContext.createProducerTemplate();
    ExecutorService sender = Executors.newSingleThreadExecutor();
    cleanup.deferCleanup(sender::shutdownNow);
    sender.submit(() -> template.sendBody("direct:input", "test message")).get();
    assertThat(sender.submit(Context::current).get()).isEqualTo(Context.root());
    assertThat(received.await(1, MINUTES)).isTrue();

    testing.waitForTraces(emitStableMessagingSemconv() ? 2 : 1);
    assertSendAndProcessMetrics(
        testing, "kafka", TOPIC, IllegalStateException.class.getName(), "0");
  }
}
