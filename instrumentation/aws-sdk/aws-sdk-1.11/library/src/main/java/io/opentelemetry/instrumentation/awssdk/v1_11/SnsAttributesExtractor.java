/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import javax.annotation.Nullable;

public class SnsAttributesExtractor implements AttributesExtractor<Request<?>, Response<?>> {
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Request<?> request) {
    String destination = findMessageDestination(request.getOriginalRequest());
    AttributesExtractorUtil.internalSet(
        attributes, MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, destination);
  }

  /*
   * Attempt to discover the destination of the SNS message by first checking for a topic ARN and
   * falling back to the target ARN. If neither is found null is returned.
   */
  private static String findMessageDestination(AmazonWebServiceRequest request) {
    String destination = RequestAccess.getTopicArn(request);
    if (destination != null) {
      return destination;
    }
    return RequestAccess.getTargetArn(request);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      Request<?> request,
      @Nullable Response<?> response,
      @Nullable Throwable error) {}
}
