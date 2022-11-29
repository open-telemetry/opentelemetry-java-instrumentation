/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

class AwsSdkSpanKindExtractor implements SpanKindExtractor<ExecutionAttributes> {
  @Override
  public SpanKind extract(ExecutionAttributes request) {
    return isSqsProducer(request) ? SpanKind.PRODUCER : SpanKind.CLIENT;
  }

  private static boolean isSqsProducer(ExecutionAttributes executionAttributes) {
    SdkRequest request =
        executionAttributes.getAttribute(TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE);
    return request
        .getClass()
        .getName()
        .equals("software.amazon.awssdk.services.sqs.model.SendMessageRequest");
  }
}
