/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrappedLambda;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.SerializationUtil;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.util.function.BiFunction;

/**
 * Wrapper for {@link io.opentelemetry.instrumentation.awslambdacore.v1_0.TracingRequestHandler}.
 * Allows for wrapping a lambda proxied through API Gateway, enabling single span tracing and HTTP
 * context propagation.
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
    return SerializationUtil.fromJson(event.getBody(), clazz);
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
      event = new APIGatewayProxyResponseEvent();
      event.setBody(SerializationUtil.toJson(result));
    }
    return event;
  }
}
