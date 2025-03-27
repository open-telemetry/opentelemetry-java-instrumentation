/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v4_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

class PulsarClientTest {

  private static final Logger logger = LoggerFactory.getLogger(PulsarClientTest.class);

  private static final DockerImageName DEFAULT_IMAGE_NAME =
      DockerImageName.parse("apachepulsar/pulsar:2.8.0");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static PulsarContainer pulsar;
  static PulsarClient client;
  static PulsarAdmin admin;
  static Producer<String> producer;
  static Consumer<String> consumer;

  static String brokerHost;
  static int brokerPort;

  static final double[] DURATION_BUCKETS =
      new double[] {
        0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0
      };

  @BeforeAll
  static void beforeAll() throws PulsarClientException {
    pulsar =
        new PulsarContainer(DEFAULT_IMAGE_NAME)
            .withEnv("PULSAR_MEM", "-Xmx128m")
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withTransactions();
    pulsar.start();

    brokerHost = pulsar.getHost();
    brokerPort = pulsar.getMappedPort(6650);
    client =
        PulsarClient.builder()
            .serviceUrl(pulsar.getPulsarBrokerUrl())
            .openTelemetry(OpenTelemetry.noop())
            .enableTransaction(true)
            .build();
    admin = PulsarAdmin.builder().serviceHttpUrl(pulsar.getHttpServiceUrl()).build();
  }

  @AfterEach
  void afterEach() throws PulsarClientException {
    if (producer != null) {
      producer.close();
    }
    if (consumer != null) {
      consumer.close();
    }
  }

  @AfterAll
  static void afterAll() throws PulsarClientException {
    if (client != null) {
      client.close();
    }
    if (admin != null) {
      admin.close();
    }
    pulsar.close();
  }

  @Test
  void testProduceBatch() throws Exception {
    String topic = "persistent://public/default/testProduceBatch";
    admin.topics().createNonPartitionedTopic(topic);
    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(true).create();

    String msg = "test";
    int sendCount = 10;
    for (int i = 0; i < sendCount; i++) {
      producer.send(msg);
    }

    assertThat(testing.metrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("messaging.publish.duration")
                    .hasUnit("s")
                    .hasDescription("Measures the duration of publish operation.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfying(
                                            equalTo(MESSAGING_SYSTEM, "pulsar"),
                                            equalTo(MESSAGING_DESTINATION_NAME, topic),
                                            equalTo(SERVER_PORT, brokerPort),
                                            equalTo(SERVER_ADDRESS, brokerHost))
                                        .hasBucketBoundaries(DURATION_BUCKETS))),
            metric ->
                assertThat(metric)
                    .hasName("messaging.client.sent.messages")
                    .hasUnit("{message}")
                    .hasDescription("Number of messages producer attempted to send to the broker.")
                    .hasLongSumSatisfying(
                        sum -> {
                          sum.hasPointsSatisfying(
                              point -> {
                                point
                                    .hasValue(sendCount)
                                    .hasAttributesSatisfying(
                                        equalTo(MESSAGING_SYSTEM, "pulsar"),
                                        equalTo(MESSAGING_DESTINATION_NAME, topic),
                                        equalTo(SERVER_PORT, brokerPort),
                                        equalTo(SERVER_ADDRESS, brokerHost));
                              });
                        }));
  }
}
