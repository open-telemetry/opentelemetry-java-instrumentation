/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.util.function.BiFunction;

/**
 * Wrapper for {@link TracingRequestHandler}. Allows for wrapping a lambda proxied through API
 * Gateway, enabling single span tracing and HTTP context propagation.
 */
public class TracingRequestApiGatewayWrapper
    extends TracingRequestWrapperBase<APIGatewayProxyRequestEvent, Object> {

  public TracingRequestApiGatewayWrapper() {
    super(TracingRequestApiGatewayWrapper::map);
  }

  // Visible for testing
  TracingRequestApiGatewayWrapper(
      OpenTelemetrySdk openTelemetrySdk,
      WrappedLambda wrappedLambda,
      BiFunction<APIGatewayProxyRequestEvent, Class<?>, Object> mapper) {
    super(openTelemetrySdk, wrappedLambda, mapper);
  }

  // Visible for testing
  static <T> T map(APIGatewayProxyRequestEvent event, Class<T> clazz) {
    try {
      return OBJECT_MAPPER.readValue(event.getBody(), clazz);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Could not map API Gateway event body to requested parameter type: " + clazz, e);
    }
  }

  @Override
  protected APIGatewayProxyResponseEvent doHandleRequest(
      APIGatewayProxyRequestEvent input, Context context) {
    Object result = super.doHandleRequest(input, context);
    APIGatewayProxyResponseEvent event;
    // map to response event if needed
    if (result instanceof APIGatewayProxyResponseEvent) {
      event = (APIGatewayProxyResponseEvent) result;
    } else {
      try {
        event = new APIGatewayProxyResponseEvent();
        event.setBody(OBJECT_MAPPER.writeValueAsString(result));
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Could not serialize return value.", e);
      }
    }
    return event;
  }
}
