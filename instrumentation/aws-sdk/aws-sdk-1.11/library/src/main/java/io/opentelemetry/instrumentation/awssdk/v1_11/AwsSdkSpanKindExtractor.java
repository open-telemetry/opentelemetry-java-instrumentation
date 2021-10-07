/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public class AwsSdkSpanKindExtractor implements SpanKindExtractor<Request<?>> {
  @Override
  public SpanKind extract(Request<?> request) {
    AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
    return (isSqsProducer(originalRequest) ? SpanKind.PRODUCER : SpanKind.CLIENT);
  }

  private static boolean isSqsProducer(AmazonWebServiceRequest request) {
    return request
        .getClass()
        .getName()
        .equals("com.amazonaws.services.sqs.model.SendMessageRequest");
  }
}
