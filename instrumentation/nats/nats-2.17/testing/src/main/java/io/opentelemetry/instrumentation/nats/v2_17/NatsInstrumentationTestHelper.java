/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.nats.client.Message;
import io.nats.client.Subscription;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.time.Duration;

@SuppressWarnings("deprecation") // using deprecated semconv
public class NatsInstrumentationTestHelper {

  public static AttributeAssertion[] messagingAttributes(
      String operation, String subject, int clientId, AttributeAssertion[] other) {
    AttributeAssertion[] standard = messagingAttributes(operation, subject, clientId);
    AttributeAssertion[] result = new AttributeAssertion[standard.length + other.length];
    System.arraycopy(standard, 0, result, 0, standard.length);
    System.arraycopy(other, 0, result, standard.length, other.length);
    return result;
  }

  public static AttributeAssertion[] messagingAttributes(
      String operation, String subject, int clientId) {
    AttributeAssertion destinationName = equalTo(MESSAGING_DESTINATION_NAME, subject);
    if (subject.startsWith("_INBOX.")) {
      destinationName = satisfies(MESSAGING_DESTINATION_NAME, name -> name.startsWith("_INBOX."));
    }

    return new AttributeAssertion[] {
      equalTo(MESSAGING_OPERATION, operation),
      equalTo(MESSAGING_SYSTEM, "nats"),
      destinationName,
      equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
      equalTo(AttributeKey.stringKey("messaging.client_id"), String.valueOf(clientId))
    };
  }

  public static void assertTraceparentHeader(Subscription subscription)
      throws InterruptedException {
    Message published = subscription.nextMessage(Duration.ofSeconds(1));
    assertThat(published.getHeaders().get("traceparent")).isNotEmpty();
  }

  private NatsInstrumentationTestHelper() {}
}
