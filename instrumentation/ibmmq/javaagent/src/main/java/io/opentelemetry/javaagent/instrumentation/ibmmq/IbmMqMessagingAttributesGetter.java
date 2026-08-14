/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import javax.annotation.Nullable;

class IbmMqMessagingAttributesGetter
    implements MessagingAttributesGetter<IbmMqRequest, IbmMqResponse> {

  @Nullable
  @Override
  public String getSystem(IbmMqRequest request) {
    return "ibm_mq";
  }

  @Nullable
  @Override
  public String getDestination(IbmMqRequest request) {
    return request.getQueueName();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(IbmMqRequest request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(IbmMqRequest request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(IbmMqRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(IbmMqRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(IbmMqRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(IbmMqRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(IbmMqRequest request, @Nullable IbmMqResponse response) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(IbmMqRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(IbmMqRequest request, @Nullable IbmMqResponse response) {
    return null;
  }
}
