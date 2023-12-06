/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Response;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributeGetter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

enum SqsReceiveRequestAttributeGetter
    implements MessagingAttributeGetter<SqsReceiveRequest, Response<?>> {
  INSTANCE;

  @Override
  public String getSystem(SqsReceiveRequest request) {
    return "AmazonSQS";
  }

  @Override
  public String getDestination(SqsReceiveRequest request) {
    Object originalRequest = request.getRequest().getOriginalRequest();
    String queueUrl = RequestAccess.getQueueUrl(originalRequest);
    int i = queueUrl.lastIndexOf('/');
    return i > 0 ? queueUrl.substring(i + 1) : null;
  }

  @Override
  public boolean isTemporaryDestination(SqsReceiveRequest request) {
    return false;
  }

  @Override
  @Nullable
  public String getConversationId(SqsReceiveRequest request) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadSize(SqsReceiveRequest request) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(SqsReceiveRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(SqsReceiveRequest request, @Nullable Response<?> response) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(SqsReceiveRequest request, String name) {
    return StreamSupport.stream(request.getMessages().spliterator(), false)
        .map(message -> message.getMessageAttribute(name))
        .filter(value -> value != null)
        .collect(Collectors.toList());
  }
}
