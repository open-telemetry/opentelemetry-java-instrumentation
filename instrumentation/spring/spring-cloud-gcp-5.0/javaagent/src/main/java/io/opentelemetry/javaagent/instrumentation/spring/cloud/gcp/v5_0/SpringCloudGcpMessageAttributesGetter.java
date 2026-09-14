/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.gcp.v5_0;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.google.cloud.spring.pubsub.support.converter.ConvertedBasicAcknowledgeablePubsubMessage;
import com.google.pubsub.v1.ProjectSubscriptionName;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

final class SpringCloudGcpMessageAttributesGetter
    implements MessagingAttributesGetter<ConvertedBasicAcknowledgeablePubsubMessage<?>, Void> {

  @Override
  public String getSystem(ConvertedBasicAcknowledgeablePubsubMessage<?> message) {
    return "gcp_pubsub";
  }

  @Override
  @Nullable
  public String getDestination(ConvertedBasicAcknowledgeablePubsubMessage<?> message) {
    ProjectSubscriptionName subscriptionName = message.getProjectSubscriptionName();
    return subscriptionName != null ? subscriptionName.getSubscription() : null;
  }

  @Override
  @Nullable
  public String getDestinationTemplate(ConvertedBasicAcknowledgeablePubsubMessage<?> message) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(ConvertedBasicAcknowledgeablePubsubMessage<?> message) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(ConvertedBasicAcknowledgeablePubsubMessage<?> message) {
    return false;
  }

  @Override
  @Nullable
  public String getConversationId(ConvertedBasicAcknowledgeablePubsubMessage<?> message) {
    return null;
  }

  @Override
  public Long getMessageBodySize(ConvertedBasicAcknowledgeablePubsubMessage<?> message) {
    return (long) message.getPubsubMessage().getData().size();
  }

  @Override
  @Nullable
  public Long getMessageEnvelopeSize(ConvertedBasicAcknowledgeablePubsubMessage<?> message) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(
      ConvertedBasicAcknowledgeablePubsubMessage<?> message, @Nullable Void unused) {
    String messageId = message.getPubsubMessage().getMessageId();
    return messageId.isEmpty() ? null : messageId;
  }

  @Override
  @Nullable
  public String getClientId(ConvertedBasicAcknowledgeablePubsubMessage<?> message) {
    return null;
  }

  @Override
  @Nullable
  public Long getBatchMessageCount(
      ConvertedBasicAcknowledgeablePubsubMessage<?> message, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(
      ConvertedBasicAcknowledgeablePubsubMessage<?> message, String name) {
    String value = message.getPubsubMessage().getAttributesMap().get(name);
    return value != null ? singletonList(value) : emptyList();
  }

  @Override
  public Collection<String> getMessageHeaderNames(
      ConvertedBasicAcknowledgeablePubsubMessage<?> message) {
    return message.getPubsubMessage().getAttributesMap().keySet();
  }
}
