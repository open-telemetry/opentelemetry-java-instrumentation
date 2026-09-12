/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.thrift.AsyncProcessFunction;
import org.apache.thrift.TBaseAsyncProcessor;

/**
 * Reflective accessor for non-public members of {@link TBaseAsyncProcessor} and {@link
 * AsyncProcessFunction}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class AsyncProcessorAccess {

  private static final Logger logger = Logger.getLogger(AsyncProcessorAccess.class.getName());

  @Nullable private static final Field processMapField = findProcessMapField();
  @Nullable private static final Method isOnewayMethod = findIsOnewayMethod();

  @Nullable
  private static Field findProcessMapField() {
    try {
      Field field = TBaseAsyncProcessor.class.getDeclaredField("processMap");
      field.setAccessible(true);
      return field;
    } catch (Throwable t) {
      logger.log(WARNING, "Failed to locate TBaseAsyncProcessor#processMap field", t);
      return null;
    }
  }

  @Nullable
  private static Method findIsOnewayMethod() {
    try {
      Method method = AsyncProcessFunction.class.getDeclaredMethod("isOneway");
      method.setAccessible(true);
      return method;
    } catch (Throwable t) {
      logger.log(WARNING, "Failed to locate AsyncProcessFunction#isOneway method", t);
      return null;
    }
  }

  // cast is safe: the field is declared as Map<String, ? extends AsyncProcessFunction<?, ?, ?, ?>>
  @SuppressWarnings("unchecked")
  @Nullable
  public static Map<String, AsyncProcessFunction<?, ?, ?, ?>> getProcessMap(
      TBaseAsyncProcessor<?> processor) {
    if (processMapField == null || isOnewayMethod == null) {
      return null;
    }
    try {
      return (Map<String, AsyncProcessFunction<?, ?, ?, ?>>) processMapField.get(processor);
    } catch (Throwable t) {
      logger.log(FINE, "Failed to read TBaseAsyncProcessor#processMap field", t);
      return null;
    }
  }

  @Nullable
  public static Boolean isOneWay(AsyncProcessFunction<?, ?, ?, ?> asyncProcessFunction) {
    if (isOnewayMethod == null) {
      return null;
    }
    try {
      Object result = isOnewayMethod.invoke(asyncProcessFunction);
      return result instanceof Boolean ? (Boolean) result : null;
    } catch (Throwable t) {
      logger.log(FINE, "Failed to invoke AsyncProcessFunction#isOneway method", t);
      return null;
    }
  }

  private AsyncProcessorAccess() {}
}
