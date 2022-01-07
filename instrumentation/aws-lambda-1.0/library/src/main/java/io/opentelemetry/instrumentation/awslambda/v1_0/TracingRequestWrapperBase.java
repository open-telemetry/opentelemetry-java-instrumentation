/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiFunction;

/**
 * Base abstract wrapper for {@link TracingRequestHandler}. Provides: - delegation to a lambda via
 * env property OTEL_INSTRUMENTATION_AWS_LAMBDA_HANDLER in package.ClassName::methodName format
 */
abstract class TracingRequestWrapperBase<I, O> extends TracingRequestHandler<I, O> {

  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final WrappedLambda wrappedLambda;
  private final Method targetMethod;
  private final BiFunction<I, Class, Object> parameterMapper;

  protected TracingRequestWrapperBase(BiFunction<I, Class, Object> parameterMapper) {
    this(
        AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk(),
        WrappedLambda.fromConfiguration(),
        parameterMapper);
  }

  // Visible for testing
  TracingRequestWrapperBase(
      OpenTelemetrySdk openTelemetrySdk,
      WrappedLambda wrappedLambda,
      BiFunction<I, Class, Object> parameterMapper) {
    super(openTelemetrySdk, WrapperConfiguration.flushTimeout());
    this.wrappedLambda = wrappedLambda;
    this.targetMethod = wrappedLambda.getRequestTargetMethod();
    this.parameterMapper = parameterMapper;
  }

  @Override
  protected O doHandleRequest(I input, Context context) {
    Object[] parameters = LambdaParameters.toArray(targetMethod, input, context, parameterMapper);
    O result;
    try {
      result = (O) targetMethod.invoke(wrappedLambda.getTargetObject(), parameters);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Method is inaccessible", e);
    } catch (InvocationTargetException e) {
      throw (e.getCause() instanceof RuntimeException
          ? (RuntimeException) e.getCause()
          : new IllegalStateException(e.getTargetException()));
    }
    return result;
  }
}
