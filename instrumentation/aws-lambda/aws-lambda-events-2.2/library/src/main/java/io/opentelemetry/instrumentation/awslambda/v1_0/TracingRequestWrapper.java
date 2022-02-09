/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrappedLambda;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.util.function.BiFunction;

/**
 * Wrapper for {@link io.opentelemetry.instrumentation.awslambdacore.v1_0.TracingRequestHandler}.
 * Allows for wrapping a regular lambda, not proxied through API Gateway. Therefore, HTTP headers
 * propagation is not supported.
 */
public class TracingRequestWrapper extends TracingRequestWrapperBase<Object, Object> {
  public TracingRequestWrapper() {
    super(TracingRequestWrapper::map);
  }

  // Visible for testing
  TracingRequestWrapper(
      OpenTelemetrySdk openTelemetrySdk,
      WrappedLambda wrappedLambda,
      BiFunction<Object, Class<?>, Object> mapper) {
    super(openTelemetrySdk, wrappedLambda, mapper);
  }

  // Visible for testing
  static <T> T map(Object jsonMap, Class<T> clazz) {
    try {
      return OBJECT_MAPPER.convertValue(jsonMap, clazz);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "Could not map input to requested parameter type: " + clazz, e);
    }
  }
}
