/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.autoconfigure;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTelemetry;

/**
 * A {@link RequestHandler2} for use as an SPI by the AWS SDK to automatically trace all requests.
 */
public class TracingRequestHandler extends RequestHandler2 {

  private static final RequestHandler2 DELEGATE =
      AwsSdkTelemetry.builder(GlobalOpenTelemetry.get())
          .setCaptureExperimentalSpanAttributes(
              Config.get()
                  .getBoolean("otel.instrumentation.aws-sdk.experimental-span-attributes", false))
          .build()
          .newRequestHandler();

  @Override
  public void beforeRequest(Request<?> request) {
    DELEGATE.beforeRequest(request);
  }

  @Override
  public AmazonWebServiceRequest beforeMarshalling(AmazonWebServiceRequest request) {
    return DELEGATE.beforeMarshalling(request);
  }

  @Override
  public void afterResponse(Request<?> request, Response<?> response) {
    DELEGATE.afterResponse(request, response);
  }

  @Override
  public void afterError(Request<?> request, Response<?> response, Exception e) {
    DELEGATE.afterError(request, response, e);
  }
}
