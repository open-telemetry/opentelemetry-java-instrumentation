/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.jms.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractJmsTest {

  protected void assertProducerSpan(
      SpanDataAssert span, String destinationName, boolean testHeaders) {
    List<AttributeAssertion> attributeAssertions =
        producerAttributeAssertions(destinationName, testHeaders);
    span.hasName(
            emitStableMessagingSemconv()
                ? destinationName.equals("(temporary)") ? "publish" : "publish " + destinationName
                : destinationName + " publish")
        .hasKind(PRODUCER)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  protected List<AttributeAssertion> producerAttributeAssertions(
      String destinationName, boolean testHeaders) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "jms"),
                messagingDestinationName(destinationName),
                oldOperation("publish"),
                operationName("publish"),
                operationType("publish"),
                satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class))));
    if (destinationName.equals("(temporary)")) {
      attributeAssertions.add(equalTo(MESSAGING_DESTINATION_TEMPORARY, true));
    }
    if (testHeaders) {
      attributeAssertions.add(
          equalTo(stringArrayKey("messaging.header.Test_Message_Header"), singletonList("test")));
      attributeAssertions.add(
          equalTo(
              stringArrayKey("messaging.header.Test_Message_Int_Header"), singletonList("1234")));
    }
    return attributeAssertions;
  }

  protected void assertConsumerSpan(
      SpanDataAssert span,
      SpanData producer,
      SpanData parent,
      String destinationName,
      String operation,
      boolean testHeaders,
      String msgId) {
    span.hasName(
            emitStableMessagingSemconv()
                ? destinationName.equals("(temporary)")
                    ? operation
                    : operation + " " + destinationName
                : destinationName + " " + operation)
        .hasKind(emitStableMessagingSemconv() && operation.equals("receive") ? CLIENT : CONSUMER);
    if (parent != null) {
      span.hasParent(parent);
    } else {
      span.hasNoParent();
    }
    if (producer != null) {
      span.hasLinks(LinkData.create(producer.getSpanContext()));
    }
    span.hasAttributesSatisfyingExactly(
        consumerAttributeAssertions(destinationName, testHeaders, operation, msgId));
  }

  protected List<AttributeAssertion> consumerAttributeAssertions(
      String destinationName, boolean testHeaders, String operation, String msgId) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "jms"),
                messagingDestinationName(destinationName),
                oldOperation(operation),
                operationName(operation),
                operationType(operation)));
    if (msgId != null) {
      attributeAssertions.add(equalTo(MESSAGING_MESSAGE_ID, msgId));
    } else {
      attributeAssertions.add(
          satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)));
    }
    if (destinationName.equals("(temporary)")) {
      attributeAssertions.add(equalTo(MESSAGING_DESTINATION_TEMPORARY, true));
    }
    if (testHeaders) {
      attributeAssertions.add(
          equalTo(stringArrayKey("messaging.header.Test_Message_Header"), singletonList("test")));
      attributeAssertions.add(
          equalTo(
              stringArrayKey("messaging.header.Test_Message_Int_Header"), singletonList("1234")));
    }
    return attributeAssertions;
  }

  private static AttributeAssertion messagingDestinationName(String destinationName) {
    return emitStableMessagingSemconv() && destinationName.equals("(temporary)")
        ? satisfies(MESSAGING_DESTINATION_NAME, val -> val.isNotEmpty())
        : equalTo(MESSAGING_DESTINATION_NAME, destinationName);
  }

  private static AttributeAssertion oldOperation(String operation) {
    return equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion operationName(String operation) {
    return equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion operationType(String operation) {
    String operationType = operation.equals("publish") ? "send" : operation;
    return equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? operationType : null);
  }
}
