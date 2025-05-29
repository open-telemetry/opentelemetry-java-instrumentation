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
import io.nats.client.Dispatcher;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("deprecation") // using deprecated semconv
class NatsInstrumentationRequestTest {

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
  void afterEach() throws InterruptedException, TimeoutException {
    subscription.drain(Duration.ofSeconds(1));
    subscription.unsubscribe();
    connection.drain(Duration.ZERO);
    connection.close();
  }


  @Test
  void testRequestTimeout() throws InterruptedException {
    // when
    testing.runWithSpan(
        "parent", () -> connection.request("sub", new byte[] {0}, Duration.ofSeconds(1)));

    // then
    assertTimeoutPublishSpan();
    assertNoHeaders();
  }

  @Test
  void testRequestFutureTimeoutBodyNoHeaders() throws InterruptedException {
    // when
    testing.runWithSpan(
        "parent",
        () -> connection.requestWithTimeout("sub", new byte[] {0}, Duration.ofSeconds(1)));

    // then
    assertCancellationPublishSpan();
    assertNoHeaders();
  }

  @Test
  void testRequestFutureTimeoutBodyWithHeaders() throws InterruptedException {
    // when
    testing.runWithSpan(
        "parent",
        () -> connection.requestWithTimeout("sub", new Headers(), new byte[] {0}, Duration.ofSeconds(1)));

    // then
    assertCancellationPublishSpan();
    assertTraceparentHeader();
  }

  @Test
  void testRequestFutureTimeoutMessageNoHeaders() throws InterruptedException {
    // given
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    testing.runWithSpan(        "parent",        () -> connection.requestWithTimeout(message, Duration.ofSeconds(1)));

    // then
    assertCancellationPublishSpan();
    assertNoHeaders();
  }

  @Test
  void testRequestFutureTimeoutMessageWithHeaders() throws InterruptedException {
    // given
    NatsMessage message =     NatsMessage.builder().subject("sub").headers(new Headers()).data("x").build();

    // when
    testing.runWithSpan(        "parent",        () -> connection.requestWithTimeout(message, Duration.ofSeconds(1)));

    // then
    assertCancellationPublishSpan();
    assertTraceparentHeader();
  }

  @Test
  void testRequestBodyNoHeaders() throws InterruptedException {
    // given
    Dispatcher dispatcher = connection.createDispatcher(
        m -> connection.publish(m.getReplyTo(), m.getData())).subscribe("sub");

    // when
    testing.runWithSpan("parent",
        () -> connection.request("sub", new byte[] {0}, Duration.ofSeconds(1)));
    connection.closeDispatcher(dispatcher);

    // then
    assertPublishSpan();
    assertNoHeaders();
  }

  @Test
  void testRequestFutureBodyNoHeaders() throws InterruptedException {
    // given
    Dispatcher dispatcher = connection.createDispatcher(
        m -> connection.publish(m.getReplyTo(), m.getData())).subscribe("sub");

    // when
    testing.runWithSpan("parent", () -> connection.request("sub", new byte[] {0}))
        .whenComplete((m, e) -> connection.closeDispatcher(dispatcher));

    // then
    assertPublishSpan();
    assertNoHeaders();
  }

  @Test
  void testRequestBodyWithHeaders() throws InterruptedException {
    // given
    Dispatcher dispatcher = connection.createDispatcher(
        m -> connection.publish(m.getReplyTo(), m.getData())).subscribe("sub");

    // when
    testing.runWithSpan("parent",
        () -> connection.request("sub", new Headers(), new byte[] {0}, Duration.ofSeconds(1)));
    connection.closeDispatcher(dispatcher);

    // then
    assertPublishSpan();
    assertTraceparentHeader();
  }

  @Test
  void testRequestFutureBodyWithHeaders() throws InterruptedException {
    // given
    Dispatcher dispatcher = connection.createDispatcher(
        m -> connection.publish(m.getReplyTo(), m.getData())).subscribe("sub");

    // when
    testing.runWithSpan("parent", () -> connection.request("sub", new Headers(), new byte[] {0}))
        .whenComplete((m, e) -> connection.closeDispatcher(dispatcher));

    // then
    assertPublishSpan();
    assertTraceparentHeader();
  }

  @Test
  void testRequestMessageNoHeaders() throws InterruptedException {
    // given
    Dispatcher dispatcher = connection.createDispatcher(
        m -> connection.publish(m.getReplyTo(), m.getData())).subscribe("sub");
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    testing.runWithSpan("parent", () -> connection.request(message, Duration.ofSeconds(1)));
    connection.closeDispatcher(dispatcher);

    // then
    assertPublishSpan();
    assertNoHeaders();
  }

  @Test
  void testRequestFutureMessageNoHeaders() throws InterruptedException {
    // given
    Dispatcher dispatcher = connection.createDispatcher(
        m -> connection.publish(m.getReplyTo(), m.getData())).subscribe("sub");
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    testing.runWithSpan("parent", () -> connection.request(message))
        .whenComplete((m, e) -> connection.closeDispatcher(dispatcher));

    // then
    assertPublishSpan();
    assertNoHeaders();
  }

  @Test
  void testRequestMessageWithHeaders() throws InterruptedException {
    // given
    Dispatcher dispatcher = connection.createDispatcher(
        m -> connection.publish(m.getReplyTo(), m.getData())).subscribe("sub");
    NatsMessage message =
        NatsMessage.builder().subject("sub").headers(new Headers()).data("x").build();

    // when
    testing.runWithSpan("parent", () -> connection.request(message, Duration.ofSeconds(1)));
    connection.closeDispatcher(dispatcher);

    // then
    assertPublishSpan();
    assertTraceparentHeader();
  }

  @Test
  void testRequestFutureMessageWithHeaders() throws InterruptedException {
    // given
    Dispatcher dispatcher = connection.createDispatcher(
        m -> connection.publish(m.getReplyTo(), m.getData())).subscribe("sub");
    NatsMessage message =
        NatsMessage.builder().subject("sub").headers(new Headers()).data("x").build();

    // when
    testing.runWithSpan("parent", () -> connection.request(message))
        .whenComplete((m, e) -> connection.closeDispatcher(dispatcher));

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
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "publish"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(
                                AttributeKey.stringKey("messaging.client_id"),
                                String.valueOf(clientId)))),
        // dispatcher publish
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.PRODUCER)));
  }

  private static void assertTimeoutPublishSpan() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("sub publish")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasException(new TimeoutException("Timed out waiting for message"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "publish"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(
                                AttributeKey.stringKey("messaging.client_id"),
                                String.valueOf(clientId)))));
  }

  private static void assertCancellationPublishSpan() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("sub publish")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasException(new CancellationException("Future cancelled, response not registered in time, check connection status."))
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
