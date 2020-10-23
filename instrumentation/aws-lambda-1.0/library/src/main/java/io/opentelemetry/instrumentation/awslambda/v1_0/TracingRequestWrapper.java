/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Wrapper for {@link TracingRequestHandler}. Allows for wrapping a regular lambda, enabling single
 * span tracing. Main lambda class should be configured as env property OTEL_LAMBDA_HANDLER in
 * package.ClassName::methodName format.
 */
public final class TracingRequestWrapper extends TracingRequestHandler {

  private WrappedLambda wrappedLambda;

  private Object[] createParametersArray(Method targetMethod, Object input, Context context) {

    Class<?>[] parameterTypes = targetMethod.getParameterTypes();

    Object[] parameters = new Object[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      // loop through to populate each index of parameter
      Object parameter = null;
      Class clazz = parameterTypes[i];
      boolean isContext = clazz.equals(Context.class);
      if (i == 0 && !isContext) {
        // first position if it's not context
        parameter = input;
      } else if (isContext) {
        // populate context
        parameter = context;
      }
      parameters[i] = parameter;
    }
    return parameters;
  }

  @Override
  protected Object doHandleRequest(Object input, Context context) {
    if (wrappedLambda == null) {
      wrappedLambda = WrappedLambda.fromConfiguration();
    }

    Method targetMethod = wrappedLambda.getRequestTargetMethod();
    Object[] parameters = createParametersArray(targetMethod, input, context);

    Object returnObj;
    try {
      returnObj = targetMethod.invoke(wrappedLambda.getTargetObject(), parameters);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Method is inaccessible", e);
    } catch (InvocationTargetException e) {
      throw (e.getCause() instanceof RuntimeException
          ? (RuntimeException) e.getCause()
          : new RuntimeException(e.getTargetException()));
    }
    return returnObj;
  }
}
