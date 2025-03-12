/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.pulsar.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.pulsar.core.PulsarTemplate;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractSpringPulsarTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static final DockerImageName DEFAULT_IMAGE_NAME =
      DockerImageName.parse("apachepulsar/pulsar:4.0.2");
  static PulsarContainer pulsarContainer;
  static ConfigurableApplicationContext applicationContext;
  static PulsarTemplate<String> pulsarTemplate;
  static PulsarClient client;
  static CountDownLatch latch = new CountDownLatch(1);
  static final String OTEL_SUBSCRIPTION = "otel-subscription";
  protected static String brokerHost;
  protected static int brokerPort;
  protected static final String OTEL_TOPIC = "persistent://public/default/otel-topic";

  @BeforeAll
  @SuppressWarnings("unchecked")
  static void setUp() throws PulsarClientException {
    pulsarContainer =
        new PulsarContainer(DEFAULT_IMAGE_NAME)
            .withEnv("PULSAR_MEM", "-Xmx128m")
            .withStartupTimeout(Duration.ofMinutes(2));
    pulsarContainer.start();
    brokerHost = pulsarContainer.getHost();
    brokerPort = pulsarContainer.getMappedPort(6650);

    SpringApplication app = new SpringApplication(ConsumerConfig.class);
    Map<String, Object> props = new HashMap<>();
    props.put("spring.main.web-application-type", "none");
    props.put("spring.pulsar.client.service-url", pulsarContainer.getPulsarBrokerUrl());
    props.put("spring.pulsar.consumer.subscription.initial-position", "earliest");
    app.setDefaultProperties(props);
    applicationContext = app.run();
    pulsarTemplate = applicationContext.getBean(PulsarTemplate.class);

    client = PulsarClient.builder().serviceUrl(pulsarContainer.getPulsarBrokerUrl()).build();
  }

  @Test
  void testSpringPulsar() throws PulsarClientException, InterruptedException {
    testing.runWithSpan(
        "parent",
        () -> {
          pulsarTemplate.send(OTEL_TOPIC, "test");
        });
    latch.await(10, TimeUnit.SECONDS);
    assertSpringPulsar();
  }

  @AfterAll
  static void teardown() {
    if (applicationContext != null) {
      applicationContext.close();
    }
    if (pulsarContainer != null) {
      pulsarContainer.stop();
    }
  }

  protected abstract void assertSpringPulsar();

  static final AttributeKey<String> MESSAGE_TYPE =
      AttributeKey.stringKey("messaging.pulsar.message.type");

  protected List<AttributeAssertion> publishAttributes() {
    return asList(
        equalTo(MESSAGING_SYSTEM, "pulsar"),
        equalTo(MESSAGING_OPERATION, "publish"),
        equalTo(MESSAGING_DESTINATION_NAME, OTEL_TOPIC),
        satisfies(MESSAGING_MESSAGE_BODY_SIZE, AbstractLongAssert::isNotNegative),
        satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotEmpty),
        equalTo(SERVER_ADDRESS, brokerHost),
        equalTo(SERVER_PORT, brokerPort),
        equalTo(MESSAGE_TYPE, "normal"));
  }

  protected List<AttributeAssertion> processAttributes() {
    return asList(
        equalTo(MESSAGING_SYSTEM, "pulsar"),
        equalTo(MESSAGING_OPERATION, "process"),
        satisfies(MESSAGING_MESSAGE_BODY_SIZE, AbstractLongAssert::isNotNegative),
        satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotEmpty),
        equalTo(MESSAGING_DESTINATION_NAME, OTEL_TOPIC));
  }

  protected List<AttributeAssertion> receiveAttributes() {
    return asList(
        equalTo(MESSAGING_SYSTEM, "pulsar"),
        equalTo(MESSAGING_OPERATION, "receive"),
        equalTo(MESSAGING_DESTINATION_NAME, OTEL_TOPIC),
        satisfies(MESSAGING_MESSAGE_BODY_SIZE, AbstractLongAssert::isNotNegative),
        satisfies(MESSAGING_BATCH_MESSAGE_COUNT, AbstractLongAssert::isNotNegative),
        equalTo(SERVER_ADDRESS, brokerHost),
        equalTo(SERVER_PORT, brokerPort));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class ConsumerConfig {
    @PulsarListener(subscriptionName = OTEL_SUBSCRIPTION, topics = OTEL_TOPIC)
    void consumer(String ignored) {
      GlobalTraceUtil.runWithSpan("consumer", () -> {});
      latch.countDown();
    }
  }
}
