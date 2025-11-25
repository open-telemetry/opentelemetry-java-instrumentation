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
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTelemetry;

/**
 * A {@link RequestHandler2} for use as an SPI by the AWS SDK to automatically trace all requests.
 */
public class TracingRequestHandler extends RequestHandler2 {

  private static final RequestHandler2 DELEGATE = buildDelegate(GlobalOpenTelemetry.get());

  private static RequestHandler2 buildDelegate(OpenTelemetry openTelemetry) {
    return AwsSdkTelemetry.builder(openTelemetry)
        .setCaptureExperimentalSpanAttributes(
            ConfigPropertiesUtil.getBoolean(
                    openTelemetry, "aws_sdk", "experimental_span_attributes")
                .orElse(false))
        .setMessagingReceiveInstrumentationEnabled(
            ConfigPropertiesUtil.getBoolean(
                    openTelemetry, "messaging", "experimental", "receive_telemetry", "enabled")
                .orElse(false))
        .setCapturedHeaders(
            ConfigPropertiesUtil.getList(
                openTelemetry, "messaging", "experimental", "capture_headers"))
        .build()
        .newRequestHandler();
  }

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
