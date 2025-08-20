/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.internal;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.function.BiFunction;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LambdaParameters {

  public static <T> Object[] toArray(
      Method targetMethod, T input, Context context, BiFunction<T, Class<?>, Object> mapper) {
    Class<?>[] parameterTypes = targetMethod.getParameterTypes();
    Object[] parameters = new Object[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      Class<?> clazz = parameterTypes[i];
      boolean isContext = clazz.equals(Context.class);
      if (isContext) {
        parameters[i] = context;
      } else if (i == 0) {
        parameters[0] = (clazz.isInstance(input) ? input : mapper.apply(input, clazz));
      }
    }
    return parameters;
  }

  public static <T> Object[] toParameters(Method targetMethod, T input, Context context) {
    Class<?>[] parameterTypes = targetMethod.getParameterTypes();
    Object[] parameters = new Object[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      Class<?> clazz = parameterTypes[i];
      boolean isContext = clazz.equals(Context.class);
      if (isContext) {
        parameters[i] = context;
      } else if (i == 0) {
        parameters[0] = input;
      }
    }
    return parameters;
  }

  public static Object toInput(
      Method targetMethod,
      InputStream inputStream,
      BiFunction<InputStream, Class<?>, Object> mapper) {
    Class<?>[] parameterTypes = targetMethod.getParameterTypes();
    for (int i = 0; i < parameterTypes.length; i++) {
      Class<?> clazz = parameterTypes[i];
      boolean isContext = clazz.equals(Context.class);
      if (i == 0 && !isContext) {
        return mapper.apply(inputStream, clazz);
      }
    }
    return null;
  }

  private LambdaParameters() {}
}
