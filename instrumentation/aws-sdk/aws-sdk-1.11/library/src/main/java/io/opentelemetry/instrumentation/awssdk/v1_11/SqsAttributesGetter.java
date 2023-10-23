/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

enum SqsAttributesGetter implements MessagingAttributesGetter<Request<?>, Response<?>> {
  INSTANCE;

  @Override
  public String getSystem(Request<?> request) {
    return "AmazonSQS";
  }

  @Override
  public String getDestination(Request<?> request) {
    Object originalRequest = request.getOriginalRequest();
    String queueUrl = RequestAccess.getQueueUrl(originalRequest);
    int i = queueUrl.lastIndexOf('/');
    return i > 0 ? queueUrl.substring(i + 1) : null;
  }

  @Override
  public boolean isTemporaryDestination(Request<?> request) {
    return false;
  }

  @Override
  @Nullable
  public String getConversationId(Request<?> request) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadSize(Request<?> request) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(Request<?> request) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(Request<?> request, @Nullable Response<?> response) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(Request<?> request, String name) {
    String value = SqsAccess.getMessageAttributes(request).get(name);
    return value != null ? Collections.singletonList(value) : Collections.emptyList();
  }
}
