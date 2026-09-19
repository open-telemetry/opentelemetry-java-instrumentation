/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing.processSpanEnabledSupplier;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelMessagingMetricsAssertions.assertSendAndProcessMetrics;
import static java.util.Collections.singleton;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

class KafkaCamelTest {

  private static final String TOPIC = "camel-test";
  private static final String NESTED_TOPIC = "nested-topic";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static CamelContext camelContext;
  private static String brokers;
  private static KafkaConsumer<String, String> nestedConsumer;
  private static final CountDownLatch received = new CountDownLatch(1);
  private static final AtomicBoolean processSpanEnabledInRoute = new AtomicBoolean();
  private static final AtomicBoolean nestedRecordReceived = new AtomicBoolean();
  private static final AtomicBoolean nestedRecordProcessed = new AtomicBoolean();

  @BeforeAll
  static void setUp() throws Exception {
    KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx256m")
            .withStartupTimeout(Duration.ofMinutes(1));
    kafka.start();
    cleanup.deferAfterAll(kafka::stop);

    brokers = kafka.getBootstrapServers().replace("PLAINTEXT://", "");
    String kafkaEndpoint =
        "kafka:" + TOPIC + "?brokers=" + brokers + "&groupId=camel-test&autoOffsetReset=earliest";
    Properties consumerProperties = new Properties();
    consumerProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
    consumerProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "nested-consumer");
    consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProperties.setProperty(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    consumerProperties.setProperty(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    nestedConsumer = new KafkaConsumer<>(consumerProperties);
    nestedConsumer.subscribe(singleton(NESTED_TOPIC));
    cleanup.deferAfterAll(nestedConsumer);

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
                      processSpanEnabledInRoute.set(processSpanEnabledSupplier().getAsBoolean());
                      ConsumerRecords<String, String> records = nestedConsumer.poll(30_000);
                      nestedRecordReceived.set(!records.isEmpty());
                      if (emitStableMessagingSemconv()) {
                        for (ConsumerRecord<String, String> ignored : records) {
                          nestedRecordProcessed.set(true);
                        }
                      }
                      throw new IllegalStateException("test");
                    });
            from("direct:consume").process(exchange -> received.countDown());
          }
        });
    camelContext.start();
    cleanup.deferAfterAll(camelContext::stop);
  }

  @Test
  void camelRecordsMetricsOverKafkaClients() throws Exception {
    Properties producerProperties = new Properties();
    producerProperties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
    producerProperties.setProperty(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    producerProperties.setProperty(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties)) {
      producer.send(new ProducerRecord<>(NESTED_TOPIC, "nested message")).get();
    }

    ProducerTemplate template = camelContext.createProducerTemplate();
    ExecutorService sender = Executors.newSingleThreadExecutor();
    cleanup.deferCleanup(sender::shutdownNow);
    sender.submit(() -> template.sendBody("direct:input", "test message")).get();
    assertThat(sender.submit(Context::current).get()).isEqualTo(Context.root());
    assertThat(received.await(1, MINUTES)).isTrue();
    assertThat(processSpanEnabledInRoute).isTrue();
    assertThat(nestedRecordReceived).isTrue();
    assertThat(nestedRecordProcessed.get()).isEqualTo(emitStableMessagingSemconv());

    testing.waitForTraces(emitStableMessagingSemconv() ? 3 : 2);
    if (emitStableMessagingSemconv()) {
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                SpanData nestedProcess = nestedProcessSpan();
                assertThat(testing.spans())
                    .filteredOn(span -> span.getSpanId().equals(nestedProcess.getParentSpanId()))
                    .singleElement()
                    .satisfies(
                        parent ->
                            assertThat(parent.getInstrumentationScopeInfo().getName())
                                .isEqualTo("io.opentelemetry.camel-2.20"));
              });
    }
    assertSendAndProcessMetrics(
        testing, "kafka", TOPIC, IllegalStateException.class.getName(), "0");
  }

  private static SpanData nestedProcessSpan() {
    List<SpanData> spans =
        testing.spans().stream()
            .filter(
                span ->
                    span.getInstrumentationScopeInfo()
                        .getName()
                        .equals("io.opentelemetry.kafka-clients-0.11"))
            .filter(span -> span.getName().equals("process " + NESTED_TOPIC))
            .collect(toList());
    assertThat(spans).hasSize(1);
    return spans.get(0);
  }
}
