/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTelemetry;
import java.util.Collections;
import java.util.List;

/**
 * A {@link RequestHandler2} for use in the agent. Unlike library instrumentation, the agent will
 * also instrument the underlying HTTP client, and we must set the context as current to be able to
 * suppress it. Also unlike library instrumentation, we are able to instrument the SDK's internal
 * classes to handle buggy behavior related to exceptions that can cause scopes to never be closed
 * otherwise which would be disastrous. We hope there won't be anymore significant changes to this
 * legacy SDK that would cause these workarounds to break in the future.
 */
// NB: If the error-handling workarounds stop working, we should consider introducing the same
// x-amzn-request-id header check in Apache instrumentation for suppressing spans that we have in
// Netty instrumentation.
public class TracingRequestHandler extends RequestHandler2 {

  public static final HandlerContextKey<Scope> SCOPE =
      new HandlerContextKey<>(Scope.class.getName());

  public static final RequestHandler2 tracingHandler;

  static {
    Configuration config = new Configuration(GlobalOpenTelemetry.get());

    tracingHandler =
        AwsSdkTelemetry.builder(GlobalOpenTelemetry.get())
            .setCaptureExperimentalSpanAttributes(config.experimentalSpanAttributes)
            .setMessagingReceiveTelemetryEnabled(config.messagingReceiveTelemetryEnabled)
            .setCapturedHeaders(config.messagingCapturedHeaders)
            .build()
            .newRequestHandler();
  }

  @Override
  public void beforeRequest(Request<?> request) {
    tracingHandler.beforeRequest(request);
    Context context = AwsSdkTelemetry.getOpenTelemetryContext(request);
    // it is possible that context is not  set by lib's handler
    if (context != null) {
      Scope scope = context.makeCurrent();
      request.addHandlerContext(SCOPE, scope);
    }
  }

  @Override
  public AmazonWebServiceRequest beforeMarshalling(AmazonWebServiceRequest request) {
    return tracingHandler.beforeMarshalling(request);
  }

  @Override
  public void afterResponse(Request<?> request, Response<?> response) {
    tracingHandler.afterResponse(request, response);
  }

  @Override
  public void afterError(Request<?> request, Response<?> response, Exception e) {
    tracingHandler.afterError(request, response, e);
    finish(request);
  }

  private static void finish(Request<?> request) {
    Scope scope = request.getHandlerContext(SCOPE);
    if (scope == null) {
      return;
    }
    scope.close();
    request.addHandlerContext(SCOPE, null);
  }

  // instrumentation/development:
  //   java:
  //     aws_sdk:
  //       experimental_span_attributes: false
  //     messaging:
  //       receive_telemetry/development:
  //         enabled: false
  //       capture_headers/development: []
  private static final class Configuration {

    private final boolean experimentalSpanAttributes;
    private final boolean messagingReceiveTelemetryEnabled;
    private final List<String> messagingCapturedHeaders;

    Configuration(OpenTelemetry openTelemetry) {
      DeclarativeConfigProperties javaConfig = empty();
      if (openTelemetry instanceof ExtendedOpenTelemetry) {
        ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
        DeclarativeConfigProperties instrumentationConfig =
            extendedOpenTelemetry.getConfigProvider().getInstrumentationConfig();
        if (instrumentationConfig != null) {
          javaConfig = instrumentationConfig.getStructured("java", empty());
        }
      }
      DeclarativeConfigProperties awsSdkConfig = javaConfig.getStructured("aws_sdk", empty());
      DeclarativeConfigProperties messagingConfig = javaConfig.getStructured("messaging", empty());

      this.experimentalSpanAttributes =
          awsSdkConfig.getBoolean("experimental_span_attributes", false);
      this.messagingReceiveTelemetryEnabled =
          messagingConfig
              .getStructured("receive_telemetry/development", empty())
              .getBoolean("enabled", false);
      this.messagingCapturedHeaders =
          messagingConfig.getScalarList(
              "capture_headers/development", String.class, Collections.emptyList());
    }
  }
}
