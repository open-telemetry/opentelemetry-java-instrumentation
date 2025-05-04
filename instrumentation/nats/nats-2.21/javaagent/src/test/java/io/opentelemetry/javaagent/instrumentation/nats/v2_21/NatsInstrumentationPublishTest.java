/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_21;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("deprecation") // using deprecated semconv
class NatsInstrumentationPublishTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static final DockerImageName natsImage = DockerImageName.parse("nats:2.11.2-alpine3.21");

  static final GenericContainer<?> natsContainer =
      new GenericContainer<>(natsImage).withExposedPorts(4222);

  static String natsUrl;
  static Connection connection;
  static Subscription subscription;
  static int clientId;

  @BeforeAll
  static void beforeAll() {
    natsContainer.start();
    natsUrl = "nats://" + natsContainer.getHost() + ":" + natsContainer.getMappedPort(4222);
  }

  @AfterAll
  static void afterAll() {
    natsContainer.close();
  }

  @BeforeEach
  void beforeEach() throws IOException, InterruptedException {
    connection = Nats.connect(natsUrl);
    subscription = connection.subscribe("sub");
    clientId = connection.getServerInfo().getClientId();
  }

  @AfterEach
  void afterEach() throws InterruptedException {
    subscription.drain(Duration.ofSeconds(1));
    connection.close();
  }

  @Test
  void testPublishBodyNoHeaders() throws InterruptedException {
    // when
    testing.runWithSpan("parent", () -> connection.publish("sub", new byte[] {0}));

    // then
    assertPublishSpan();
    assertNoHeaders();
  }

  @Test
  void testPublishBodyWithHeaders() throws InterruptedException {
    // when
    testing.runWithSpan("parent", () -> connection.publish("sub", new Headers(), new byte[] {0}));

    // then
    assertPublishSpan();
    assertTraceparentHeader();
  }

  @Test
  void testPublishBodyReplyToNoHeaders() throws InterruptedException {
    // when
    testing.runWithSpan("parent", () -> connection.publish("sub", "rt", new byte[] {0}));

    // then
    assertPublishSpan();
    assertNoHeaders();
  }

  @Test
  void testPublishBodyReplyToWithHeaders() throws InterruptedException {
    // when
    testing.runWithSpan(
        "parent", () -> connection.publish("sub", "rt", new Headers(), new byte[] {0}));

    // then
    assertPublishSpan();
    assertTraceparentHeader();
  }

  @Test
  void testPublishMessageNoHeaders() throws InterruptedException {
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    testing.runWithSpan("parent", () -> connection.publish(message));

    // then
    assertPublishSpan();
    assertNoHeaders();
  }

  @Test
  void testPublishMessageWithHeaders() throws InterruptedException {
    NatsMessage message =
        NatsMessage.builder().subject("sub").data("x").headers(new Headers()).build();

    // when
    testing.runWithSpan("parent", () -> connection.publish(message));

    // then
    assertPublishSpan();
    assertTraceparentHeader();
  }

  private static void assertPublishSpan() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("sub publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "publish"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(
                                AttributeKey.stringKey("messaging.client_id"),
                                String.valueOf(clientId)))));
  }

  private static void assertNoHeaders() throws InterruptedException {
    Message published = subscription.nextMessage(Duration.ofSeconds(1));
    assertThat(published.getHeaders()).isNull();
  }

  private static void assertTraceparentHeader() throws InterruptedException {
    Message published = subscription.nextMessage(Duration.ofSeconds(1));
    assertThat(published.getHeaders().get("traceparent")).isNotEmpty();
  }
}
