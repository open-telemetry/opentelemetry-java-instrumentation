/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CLIENT_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPLATE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Options;
import io.nats.client.Subscription;
import io.nats.client.api.ServerInfo;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
class OpenTelemetryMessageTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void ackSync() throws Exception {
    Connection connection = connection();
    AtomicBoolean ackSyncCalled = new AtomicBoolean();
    Message message = message(connection, ackSyncCalled);
    Message wrapped =
        OpenTelemetryMessage.wrap(
            message,
            NatsInstrumenterFactory.createSettleInstrumenter(
                testing.getOpenTelemetry(), IncludeExclude.builder().build()));

    testing.runWithSpan("parent", () -> wrapped.ackSync(Duration.ofSeconds(1)));

    assertThat(ackSyncCalled).isTrue();

    String subject = "$JS.ACK.stream.consumer.1.2.3.4.5";
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "ack $JS.ACK" : "$JS.ACK settle")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            settlementAttributes(subject).toArray(new AttributeAssertion[0]))));
    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.nats-2.17",
          "messaging.client.operation.duration",
          metrics ->
              metrics.satisfiesExactly(
                  metric ->
                      assertThat(metric)
                          .hasHistogramSatisfying(
                              histogram ->
                                  histogram.hasPointsSatisfying(
                                      point ->
                                          point
                                              .hasSumGreaterThan(0)
                                              .hasAttributesSatisfyingExactly(
                                                  equalTo(MESSAGING_OPERATION_NAME, "ack"),
                                                  equalTo(MESSAGING_SYSTEM, "nats"),
                                                  equalTo(
                                                      MESSAGING_DESTINATION_TEMPLATE, "$JS.ACK"),
                                                  equalTo(MESSAGING_OPERATION_TYPE, "settle"))))));
    }
  }

  @Test
  @SuppressWarnings("PreferJavaTimeOverload") // explicitly test the legacy overload
  void nakWithDelayUsesNativeBodyForNonPositiveDelays() throws Exception {
    Connection connection = connection();
    Message message = message(connection, new AtomicBoolean());
    Message wrapped =
        OpenTelemetryMessage.wrap(
            message,
            NatsInstrumenterFactory.createSettleInstrumenter(
                testing.getOpenTelemetry(), IncludeExclude.builder().build()));

    testing.runWithSpan(
        "parent",
        () -> {
          wrapped.nakWithDelay(Duration.ZERO);
          wrapped.nakWithDelay(Duration.ofNanos(-1));
          wrapped.nakWithDelay(Duration.ofNanos(1));
          wrapped.nakWithDelay(0L);
          wrapped.nakWithDelay(-1L);
          wrapped.nakWithDelay(1L);
        });

    String subject = "$JS.ACK.stream.consumer.1.2.3.4.5";
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                settlementSpan(trace, subject, "nak", 4),
                settlementSpan(trace, subject, "nak", 4),
                settlementSpan(trace, subject, "nak", 16),
                settlementSpan(trace, subject, "nak", 4),
                settlementSpan(trace, subject, "nak", 4),
                settlementSpan(trace, subject, "nak", 22)));
  }

  @Test
  void nextMessageWrapsJetStreamMessage() throws InterruptedException {
    Connection connection = connection();
    Message message = message(connection, new AtomicBoolean());
    Subscription subscription =
        (Subscription)
            Proxy.newProxyInstance(
                Subscription.class.getClassLoader(),
                new Class<?>[] {Subscription.class},
                (proxy, method, args) -> method.getName().equals("nextMessage") ? message : null);
    Subscription wrapped =
        OpenTelemetrySubscription.wrap(
            subscription,
            NatsInstrumenterFactory.createSettleInstrumenter(
                testing.getOpenTelemetry(), IncludeExclude.builder().build()));

    Message wrappedMessage = wrapped.nextMessage(Duration.ZERO);
    assertThat(wrappedMessage).isNotSameAs(message);
    testing.runWithSpan("parent", wrappedMessage::ack);

    String subject = "$JS.ACK.stream.consumer.1.2.3.4.5";
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                settlementSpan(trace, subject, "ack", 4)));
  }

  @Test
  void jetStreamSubscribeWrapsSubscription() throws Exception {
    Connection connection = connection();
    Message message = message(connection, new AtomicBoolean());
    JetStreamSubscription subscription =
        (JetStreamSubscription)
            Proxy.newProxyInstance(
                JetStreamSubscription.class.getClassLoader(),
                new Class<?>[] {JetStreamSubscription.class},
                (proxy, method, args) -> method.getName().equals("nextMessage") ? message : null);
    JetStream jetStream =
        (JetStream)
            Proxy.newProxyInstance(
                JetStream.class.getClassLoader(),
                new Class<?>[] {JetStream.class},
                (proxy, method, args) ->
                    method.getName().equals("subscribe") ? subscription : null);
    JetStream wrapped =
        OpenTelemetryJetStream.wrap(
            jetStream,
            NatsInstrumenterFactory.createSettleInstrumenter(
                testing.getOpenTelemetry(), IncludeExclude.builder().build()));

    JetStreamSubscription wrappedSubscription = wrapped.subscribe("stream");
    assertThat(wrappedSubscription).isNotSameAs(subscription);
    Message wrappedMessage = wrappedSubscription.nextMessage(Duration.ZERO);
    assertThat(wrappedMessage).isNotSameAs(message);
    testing.runWithSpan("parent", wrappedMessage::ack);

    String subject = "$JS.ACK.stream.consumer.1.2.3.4.5";
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                settlementSpan(trace, subject, "ack", 4)));
  }

  private static List<AttributeAssertion> settlementAttributes(String subject) {
    return settlementAttributes(subject, "ack", 4L);
  }

  private static List<AttributeAssertion> settlementAttributes(
      String subject, String operation, long messageBodySize) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    assertions.add(equalTo(MESSAGING_OPERATION_NAME, operation));
    assertions.add(equalTo(MESSAGING_SYSTEM, "nats"));
    assertions.add(equalTo(MESSAGING_DESTINATION_NAME, subject));
    assertions.add(equalTo(MESSAGING_DESTINATION_TEMPLATE, "$JS.ACK"));
    if (emitOldMessagingSemconv()) {
      assertions.add(equalTo(MESSAGING_OPERATION, "settle"));
      assertions.add(equalTo(MESSAGING_MESSAGE_BODY_SIZE, messageBodySize));
      assertions.add(equalTo(stringKey("messaging.client_id"), "123"));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(equalTo(MESSAGING_OPERATION_TYPE, "settle"));
      assertions.add(equalTo(MESSAGING_CLIENT_ID, "123"));
    }
    return assertions;
  }

  private static Consumer<SpanDataAssert> settlementSpan(
      TraceAssert trace, String subject, String operation, long messageBodySize) {
    return span ->
        span.hasName(emitStableMessagingSemconv() ? operation + " $JS.ACK" : "$JS.ACK settle")
            .hasKind(SpanKind.CLIENT)
            .hasParent(trace.getSpan(0))
            .hasAttributesSatisfyingExactly(
                settlementAttributes(subject, operation, messageBodySize)
                    .toArray(new AttributeAssertion[0]));
  }

  private static Connection connection() {
    ServerInfo serverInfo = new ServerInfo("{\"client_id\":123}");
    Options options = Options.builder().build();
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getServerInfo")) {
                return serverInfo;
              }
              if (method.getName().equals("getOptions")) {
                return options;
              }
              return null;
            });
  }

  private static Message message(Connection connection, AtomicBoolean ackSyncCalled) {
    return (Message)
        Proxy.newProxyInstance(
            Message.class.getClassLoader(),
            new Class<?>[] {Message.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getConnection")) {
                return connection;
              }
              if (method.getName().equals("getReplyTo")) {
                return "$JS.ACK.stream.consumer.1.2.3.4.5";
              }
              if (method.getName().equals("isJetStream")) {
                return true;
              }
              if (method.getName().equals("getSubject")) {
                return "subject";
              }
              if (method.getName().equals("ackSync")) {
                ackSyncCalled.set(true);
              }
              return null;
            });
  }
}
