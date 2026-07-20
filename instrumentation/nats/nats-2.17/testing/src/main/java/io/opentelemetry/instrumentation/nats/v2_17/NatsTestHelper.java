/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPLATE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.nats.client.Message;
import io.nats.client.Subscription;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation") // using deprecated semconv
class NatsTestHelper {

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_CLIENT_ID_OLD =
      stringKey("messaging.client_id");
  private static final AttributeKey<String> MESSAGING_CLIENT_ID = stringKey("messaging.client.id");

  static AttributeAssertion[] messagingAttributes(
      String operation, String subject, int clientId, AttributeAssertion other) {
    return messagingAttributes(operation, subject, clientId, new AttributeAssertion[] {other});
  }

  static AttributeAssertion[] messagingAttributes(
      String operation, String subject, int clientId, AttributeAssertion[] other) {
    AttributeAssertion[] standard = messagingAttributes(operation, subject, clientId);
    AttributeAssertion[] result = new AttributeAssertion[standard.length + other.length];
    System.arraycopy(standard, 0, result, 0, standard.length);
    System.arraycopy(other, 0, result, standard.length, other.length);
    return result;
  }

  static AttributeAssertion[] messagingAttributes(String operation, String subject, int clientId) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    assertions.add(equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? operation : null));
    assertions.add(
        equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operation : null));
    assertions.add(
        equalTo(
            MESSAGING_OPERATION_TYPE,
            emitStableMessagingSemconv()
                ? operation.equals("publish") ? "send" : operation
                : null));
    assertions.add(equalTo(MESSAGING_SYSTEM, "nats"));
    if (subject.equals("(temporary)") && emitStableMessagingSemconv()) {
      assertions.add(satisfies(MESSAGING_DESTINATION_NAME, val -> val.startsWith("_INBOX.")));
      assertions.add(equalTo(MESSAGING_DESTINATION_TEMPLATE, "_INBOX."));
      assertions.add(equalTo(MESSAGING_DESTINATION_TEMPORARY, true));
    } else {
      assertions.add(equalTo(MESSAGING_DESTINATION_NAME, subject));
      if (subject.equals("(temporary)")) {
        assertions.add(equalTo(MESSAGING_DESTINATION_TEMPORARY, true));
      }
    }
    assertions.add(equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1));
    assertions.add(
        equalTo(
            MESSAGING_CLIENT_ID_OLD, emitOldMessagingSemconv() ? String.valueOf(clientId) : null));
    assertions.add(
        equalTo(
            MESSAGING_CLIENT_ID, emitStableMessagingSemconv() ? String.valueOf(clientId) : null));
    return assertions.toArray(new AttributeAssertion[0]);
  }

  static void assertTraceparentHeader(Subscription subscription) throws InterruptedException {
    Message published = subscription.nextMessage(Duration.ofSeconds(10));
    assertThat(published).isNotNull();
    assertThat(published.getHeaders().get("traceparent")).isNotEmpty();
  }

  private NatsTestHelper() {}
}
