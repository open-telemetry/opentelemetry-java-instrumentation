/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.TracingRequestStreamWrapper;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.ApiGatewayProxyRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.MapUtils;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrappedLambda;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.SerializationUtil;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

/**
 * Wrapper for {@link com.amazonaws.services.lambda.runtime.RequestHandler} based Lambda handlers.
 */
public class TracingRequestWrapper extends TracingRequestStreamWrapper {
  public TracingRequestWrapper() {
    super();
  }

  // Visible for testing
  TracingRequestWrapper(OpenTelemetrySdk openTelemetrySdk, WrappedLambda wrappedLambda) {
    super(openTelemetrySdk, wrappedLambda);
  }

  @Override
  protected final AwsLambdaRequest createRequest(
      InputStream inputStream, Context context, ApiGatewayProxyRequest proxyRequest) {
    Method targetMethod = wrappedLambda.getRequestTargetMethod();
    Object input = LambdaParameters.toInput(targetMethod, inputStream, TracingRequestWrapper::map);
    return AwsLambdaRequest.create(context, input, extractHeaders(input));
  }

  protected Map<String, String> extractHeaders(Object input) {
    if (input instanceof APIGatewayProxyRequestEvent) {
      return MapUtils.emptyIfNull(((APIGatewayProxyRequestEvent) input).getHeaders());
    }
    return Collections.emptyMap();
  }

  @Override
  protected final void doHandleRequest(
      InputStream input, OutputStream output, Context context, AwsLambdaRequest request) {
    Method targetMethod = wrappedLambda.getRequestTargetMethod();
    Object[] parameters = LambdaParameters.toParameters(targetMethod, request.getInput(), context);
    try {
      Object result = targetMethod.invoke(wrappedLambda.getTargetObject(), parameters);
      SerializationUtil.toJson(output, result);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Method is inaccessible", e);
    } catch (InvocationTargetException e) {
      throw (e.getCause() instanceof RuntimeException
          ? (RuntimeException) e.getCause()
          : new IllegalStateException(e.getTargetException()));
    }
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  // Used for testing
  <INPUT, OUTPUT> OUTPUT handleRequest(INPUT input, Context context) throws IOException {
    byte[] inputJsonData = SerializationUtil.toJsonData(input);
    ByteArrayInputStream inputStream = new ByteArrayInputStream(inputJsonData);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    super.handleRequest(inputStream, outputStream, context);

    byte[] outputJsonData = outputStream.toByteArray();
    return (OUTPUT)
        SerializationUtil.fromJson(
            new ByteArrayInputStream(outputJsonData),
            wrappedLambda.getRequestTargetMethod().getReturnType());
  }

  // Visible for testing
  static <T> T map(InputStream inputStream, Class<T> clazz) {
    try {
      return SerializationUtil.fromJson(inputStream, clazz);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "Could not map input to requested parameter type: " + clazz, e);
    }
  }
}
