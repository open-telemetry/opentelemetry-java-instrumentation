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
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.InstrumentationConfigUtil;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTelemetry;
import java.util.Optional;

/**
 * A {@link RequestHandler2} for use as an SPI by the AWS SDK to automatically trace all requests.
 */
public class TracingRequestHandler extends RequestHandler2 {

  private static final RequestHandler2 DELEGATE = buildDelegate(GlobalOpenTelemetry.get());

  private static RequestHandler2 buildDelegate(OpenTelemetry openTelemetry) {
    ConfigProvider configProvider = ConfigPropertiesUtil.getConfigProvider(openTelemetry);
    return AwsSdkTelemetry.builder(openTelemetry)
        .setCaptureExperimentalSpanAttributes(
            Optional.ofNullable(
                    InstrumentationConfigUtil.getOrNull(
                        configProvider,
                        config -> config.getBoolean("span_attributes/development"),
                        "java",
                        "aws_sdk"))
                .orElse(false))
        .setMessagingReceiveInstrumentationEnabled(
            Optional.ofNullable(
                    InstrumentationConfigUtil.getOrNull(
                        configProvider,
                        config -> config.getBoolean("messaging.receive_telemetry/development"),
                        "java",
                        "aws_sdk"))
                .orElse(false))
        .setCapturedHeaders(
            Optional.ofNullable(
                    InstrumentationConfigUtil.getOrNull(
                        configProvider,
                        config -> config.getScalarList("capture_headers/development", String.class),
                        "java",
                        "messaging"))
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
