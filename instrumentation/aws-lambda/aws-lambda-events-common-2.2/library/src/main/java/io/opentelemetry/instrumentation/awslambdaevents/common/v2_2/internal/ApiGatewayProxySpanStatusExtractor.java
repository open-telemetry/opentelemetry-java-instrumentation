/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import javax.annotation.Nullable;

final class ApiGatewayProxySpanStatusExtractor
    implements SpanStatusExtractor<AwsLambdaRequest, Object> {

  @Override
  public void extract(
      SpanStatusBuilder spanStatusBuilder,
      AwsLambdaRequest request,
      @Nullable Object response,
      @Nullable Throwable error) {
    if (request.getInput() instanceof APIGatewayProxyRequestEvent
        && response instanceof APIGatewayProxyResponseEvent) {
      Integer statusCode = ((APIGatewayProxyResponseEvent) response).getStatusCode();
      if (statusCode != null && (statusCode >= 500 || statusCode < 100)) {
        spanStatusBuilder.setStatus(StatusCode.ERROR);
        return;
      }
    }
    SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, response, error);
  }
}
