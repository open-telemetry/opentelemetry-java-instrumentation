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
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

enum SqsAttributesGetter implements MessagingAttributesGetter<ExecutionAttributes, Response> {
  INSTANCE;

  @Override
  public String getSystem(ExecutionAttributes request) {
    return "AmazonSQS";
  }

  @Override
  public String getDestination(ExecutionAttributes request) {
    SdkRequest sdkRequest = request.getAttribute(TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE);
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
  public boolean isTemporaryDestination(ExecutionAttributes request) {
    return false;
  }

  @Override
  @Nullable
  public String getConversationId(ExecutionAttributes request) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadSize(ExecutionAttributes request) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(ExecutionAttributes request) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(ExecutionAttributes request, @Nullable Response response) {
    if (response != null && response.getSdkResponse() != null) {
      SdkResponse sdkResponse = response.getSdkResponse();
      return SqsAccess.getMessageId(sdkResponse);
    }
    return null;
  }

  @Override
  public List<String> getMessageHeader(ExecutionAttributes request, String name) {
    // TODO: not implemented
    return Collections.emptyList();
  }
}
