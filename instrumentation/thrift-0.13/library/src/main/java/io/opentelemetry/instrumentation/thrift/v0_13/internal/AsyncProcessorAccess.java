/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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

  private static final Field processMapField = findProcessMapField();
  private static final Method isOnewayMethod = findIsOnewayMethod();

  private static Field findProcessMapField() {
    try {
      Field field = TBaseAsyncProcessor.class.getDeclaredField("processMap");
      field.setAccessible(true);
      return field;
    } catch (Throwable t) {
      logger.log(Level.WARNING, "Failed to locate TBaseAsyncProcessor#processMap field", t);
      return null;
    }
  }

  private static Method findIsOnewayMethod() {
    try {
      Method method = AsyncProcessFunction.class.getDeclaredMethod("isOneway");
      method.setAccessible(true);
      return method;
    } catch (Throwable t) {
      logger.log(Level.WARNING, "Failed to locate AsyncProcessFunction#isOneway method", t);
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public static Map<String, AsyncProcessFunction<?, ?, ?, ?>> getProcessMap(
      TBaseAsyncProcessor<?> processor) {
    if (processMapField == null) {
      return Collections.emptyMap();
    }
    try {
      return (Map<String, AsyncProcessFunction<?, ?, ?, ?>>) processMapField.get(processor);
    } catch (Throwable t) {
      logger.log(Level.FINE, "Failed to read TBaseAsyncProcessor#processMap field", t);
      return Collections.emptyMap();
    }
  }

  public static boolean isOneWay(AsyncProcessFunction<?, ?, ?, ?> asyncProcessFunction) {
    if (isOnewayMethod == null) {
      return false;
    }
    try {
      Object result = isOnewayMethod.invoke(asyncProcessFunction);
      return result instanceof Boolean && (Boolean) result;
    } catch (Throwable t) {
      logger.log(Level.FINE, "Failed to invoke AsyncProcessFunction#isOneway method", t);
      return false;
    }
  }

  private AsyncProcessorAccess() {}
}
