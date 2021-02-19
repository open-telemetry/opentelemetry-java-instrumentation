/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.opentelemetry.sdk.OpenTelemetrySdk;

/**
 * Wrapper for {@link TracingRequestHandler}. Allows for wrapping a lambda proxied through API
 * Gateway, enabling single span tracing and HTTP context propagation.
 */
public class TracingRequestApiGatewayWrapper
    extends TracingRequestWrapperBase<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  public TracingRequestApiGatewayWrapper() {
    super();
  }

  // Visible for testing
  TracingRequestApiGatewayWrapper(OpenTelemetrySdk openTelemetrySdk) {
    super(openTelemetrySdk);
  }
}
