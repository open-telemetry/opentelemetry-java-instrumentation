/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

// this class is only used from SqsAccess from method with @NoMuzzle annotation
final class SqsImpl {
  private SqsImpl() {}

  public static void init() {
    // called from advice
  }

  public static SdkRequest injectIntoSqsSendMessageRequest(
      TextMapPropagator messagingPropagator,
      SdkRequest rawRequest,
      io.opentelemetry.context.Context otelContext) {
    SendMessageRequest request = (SendMessageRequest) rawRequest;
    Map<String, MessageAttributeValue> messageAttributes =
        new HashMap<>(request.messageAttributes());

    // Need to use a full method to allow @NoMuzzle annotation (@NoMuzzle is not transitively
    // applied to called methods)
    messagingPropagator.inject(otelContext, messageAttributes, SqsImpl::injectSqsAttribute);

    if (messageAttributes.size() > 10) { // Too many attributes, we don't want to break the call.
      return request;
    }
    return request.toBuilder().messageAttributes(messageAttributes).build();
  }

  private static void injectSqsAttribute(
      @Nullable Map<String, MessageAttributeValue> carrier, String k, String v) {
    if (carrier != null) {
      carrier.put(k, MessageAttributeValue.builder().stringValue(v).dataType("String").build());
    }
  }
}
