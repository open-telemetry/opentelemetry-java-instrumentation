/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

// this class is only used from SnsAccess from method with @NoMuzzle annotation
class SnsImpl {
  static {
    // Force loading of SnsClient; this ensures that an exception is thrown at this point when the
    // SNS library is not present, which will cause SnsAccess to have enabled=false in library mode.
    @SuppressWarnings("unused")
    String ensureLoadedDummy = SnsClient.class.getName();
  }

  private SnsImpl() {}

  static SdkRequest modifyRequest(
      SdkRequest request, Context otelContext, TextMapPropagator messagingPropagator) {
    if (messagingPropagator == null) {
      return null;
    } else if (request instanceof PublishRequest) {
      return injectIntoPublishRequest((PublishRequest) request, otelContext, messagingPropagator);
    } else {
      // NB: We do not support PublishBatchRequest which was only introduced in 2.17.84.
      // To add support, some targeted use of @NoMuzzle + checks that the needed class
      // is available should work. See
      // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/8830#discussion_r1247570985
      return null;
    }
  }

  private static SdkRequest injectIntoPublishRequest(
      PublishRequest request, Context otelContext, TextMapPropagator messagingPropagator) {
    // Note: Code is 1:1 copy & paste from SQS, but due to different types (packages) cannot be
    // reused.
    Map<String, MessageAttributeValue> messageAttributes =
        new HashMap<>(request.messageAttributes());
    if (!injectIntoMessageAttributes(messageAttributes, otelContext, messagingPropagator)) {
      return request;
    }
    return request.toBuilder().messageAttributes(messageAttributes).build();
  }

  private static boolean injectIntoMessageAttributes(
      Map<String, MessageAttributeValue> messageAttributes,
      io.opentelemetry.context.Context otelContext,
      TextMapPropagator messagingPropagator) {
    // Note: Code is 1:1 copy & paste from SQS, but due to different types (packages) cannot be
    // reused.
    messagingPropagator.inject(
        otelContext,
        messageAttributes,
        (carrier, k, v) -> {
          carrier.put(k, MessageAttributeValue.builder().stringValue(v).dataType("String").build());
        });

    // Return whether the injection resulted in an attribute count that is still supported.
    // See https://docs.aws.amazon.com/sns/latest/dg/sns-message-attributes.html
    // While non-raw delivery would support an arbitrary number, that is something configured in
    // the subscription, and adding more attributes might result in odd behavior (e.g. we might
    // push out other attributes)
    return messageAttributes.size() <= 10;
  }
}
