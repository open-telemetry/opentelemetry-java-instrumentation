/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

enum SqsProcessRequestAttributesGetter
    implements MessagingAttributesGetter<SqsProcessRequest, Void> {
  INSTANCE;

  @Override
  public String getSystem(SqsProcessRequest request) {
    return "AmazonSQS";
  }

  @Override
  public String getDestination(SqsProcessRequest request) {
    Object originalRequest = request.getRequest().getOriginalRequest();
    String queueUrl = RequestAccess.getQueueUrl(originalRequest);
    int i = queueUrl.lastIndexOf('/');
    return i > 0 ? queueUrl.substring(i + 1) : null;
  }

  @Override
  public boolean isTemporaryDestination(SqsProcessRequest request) {
    return false;
  }

  @Override
  @Nullable
  public String getConversationId(SqsProcessRequest request) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadSize(SqsProcessRequest request) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(SqsProcessRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(SqsProcessRequest request, @Nullable Void response) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(SqsProcessRequest request, String name) {
    String value = SqsAccess.getMessageAttributes(request.getRequest()).get(name);
    return value != null ? Collections.singletonList(value) : Collections.emptyList();
  }
}
