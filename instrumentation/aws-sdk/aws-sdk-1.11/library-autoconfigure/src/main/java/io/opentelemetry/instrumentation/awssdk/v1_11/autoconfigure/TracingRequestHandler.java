/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.autoconfigure;

import static java.util.Collections.emptyList;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.LibraryConfigUtil;
import io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTelemetry;
import java.util.Optional;

/**
 * A {@link RequestHandler2} for use as an SPI by the AWS SDK to automatically trace all requests.
 */
public class TracingRequestHandler extends RequestHandler2 {

  private static final RequestHandler2 DELEGATE = buildDelegate(GlobalOpenTelemetry.get());

  private static RequestHandler2 buildDelegate(OpenTelemetry openTelemetry) {
    ExtendedDeclarativeConfigProperties messaging =
        LibraryConfigUtil.getJavaInstrumentationConfig(openTelemetry, "messaging");
    return AwsSdkTelemetry.builder(openTelemetry)
        .setCaptureExperimentalSpanAttributes(
            LibraryConfigUtil.getJavaInstrumentationConfig(openTelemetry, "aws_sdk")
                .getBoolean("span_attributes/development", false))
        .setMessagingReceiveTelemetryEnabled(
            messaging.get("receive_telemetry/development").getBoolean("enabled", false))
        .setCapturedHeaders(
            Optional.ofNullable(
                    messaging.getScalarList("capture_headers/development", String.class))
                .orElse(emptyList()))
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
