/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;

import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;

@SuppressWarnings("deprecation") // using deprecated semconv
final class SpringIntegrationTestHelper {

  static AttributeAssertion[] messagingAttributes(String operation, String destinationName) {
    return messagingAttributes(operation, destinationName, new AttributeAssertion[0]);
  }

  static AttributeAssertion[] messagingAttributes(
      String operation, String destinationName, AttributeAssertion... additionalAssertions) {
    AttributeAssertion[] standard =
        new AttributeAssertion[] {
          equalTo(MESSAGING_SYSTEM, emitStableMessagingSemconv() ? "spring_integration" : null),
          equalTo(
              MESSAGING_DESTINATION_NAME, emitStableMessagingSemconv() ? destinationName : null),
          equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? operation : null),
          equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operation : null),
          equalTo(
              MESSAGING_OPERATION_TYPE,
              emitStableMessagingSemconv()
                  ? operation.equals("publish") ? "send" : operation
                  : null)
        };
    AttributeAssertion[] result =
        new AttributeAssertion[standard.length + additionalAssertions.length];
    System.arraycopy(standard, 0, result, 0, standard.length);
    System.arraycopy(additionalAssertions, 0, result, standard.length, additionalAssertions.length);
    return result;
  }

  private SpringIntegrationTestHelper() {}
}
