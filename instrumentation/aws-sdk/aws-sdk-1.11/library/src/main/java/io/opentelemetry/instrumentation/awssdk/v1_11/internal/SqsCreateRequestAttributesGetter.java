/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

final class SqsCreateRequestAttributesGetter
    implements MessagingAttributesGetter<SqsCreateRequest, Void> {

  // copied from MessagingIncubatingAttributes.MessagingSystemIncubatingValues
  private static final String AWS_SQS = "aws_sqs";

  @Override
  public String getSystem(SqsCreateRequest request) {
    return AWS_SQS;
  }

  @Override
  public String getDestination(SqsCreateRequest request) {
    return request.getDestination();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(SqsCreateRequest request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(SqsCreateRequest request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(SqsCreateRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(SqsCreateRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(SqsCreateRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(SqsCreateRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(SqsCreateRequest request, @Nullable Void response) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(SqsCreateRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(SqsCreateRequest request, @Nullable Void response) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(SqsCreateRequest request, String name) {
    String value = request.getMessageAttribute(name);
    return value != null ? singletonList(value) : emptyList();
  }

  @Override
  public Collection<String> getMessageHeaderNames(SqsCreateRequest request) {
    return request.getMessageAttributeNames();
  }
}
