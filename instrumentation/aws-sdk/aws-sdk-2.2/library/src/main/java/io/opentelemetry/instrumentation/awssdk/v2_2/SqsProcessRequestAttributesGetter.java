/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;

enum SqsProcessRequestAttributesGetter
    implements MessagingAttributesGetter<SqsProcessRequest, Void> {
  INSTANCE;

  @Override
  public String getSystem(SqsProcessRequest request) {
    return "AmazonSQS";
  }

  @Override
  public String getDestination(SqsProcessRequest request) {
    SdkRequest sdkRequest =
        request.getRequest().getAttribute(TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE);
    String queueUrl = SqsAccess.getQueueUrl(sdkRequest);
    if (queueUrl != null) {
      int i = queueUrl.lastIndexOf('/');
      if (i > 0) {
        return queueUrl.substring(i + 1);
      }
    }
    return null;
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
    return request.getMessage().getMessageId();
  }

  @Override
  public List<String> getMessageHeader(SqsProcessRequest request, String name) {
    String value = request.getMessage().getMessageAttribute(name);
    return value != null ? Collections.singletonList(value) : Collections.emptyList();
  }
}
