/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.jms.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;

import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractJmsTest {

  protected void assertProducerSpan(
      SpanDataAssert span, String destinationName, boolean testHeaders) {
    List<AttributeAssertion> attributeAssertions =
        producerAttributeAssertions(destinationName, testHeaders);
    span.hasName(destinationName + " publish")
        .hasKind(PRODUCER)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  protected List<AttributeAssertion> producerAttributeAssertions(
      String destinationName, boolean testHeaders) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "jms"),
                equalTo(MESSAGING_DESTINATION_NAME, destinationName),
                equalTo(MESSAGING_OPERATION, "publish"),
                satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class))));
    if (destinationName.equals("(temporary)")) {
      attributeAssertions.add(equalTo(MESSAGING_DESTINATION_TEMPORARY, true));
    }
    if (testHeaders) {
      attributeAssertions.add(
          equalTo(
              stringArrayKey("messaging.header.test_message_header"),
              Collections.singletonList("test")));
      attributeAssertions.add(
          equalTo(
              stringArrayKey("messaging.header.test_message_int_header"),
              Collections.singletonList("1234")));
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
    span.hasName(destinationName + " " + operation).hasKind(CONSUMER);
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

  @SuppressWarnings("deprecation") // using deprecated semconv
  protected List<AttributeAssertion> consumerAttributeAssertions(
      String destinationName, boolean testHeaders, String operation, String msgId) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "jms"),
                equalTo(MESSAGING_DESTINATION_NAME, destinationName),
                equalTo(MESSAGING_OPERATION, operation)));
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
          equalTo(
              stringArrayKey("messaging.header.test_message_header"),
              Collections.singletonList("test")));
      attributeAssertions.add(
          equalTo(
              stringArrayKey("messaging.header.test_message_int_header"),
              Collections.singletonList("1234")));
    }
    return attributeAssertions;
  }
}
