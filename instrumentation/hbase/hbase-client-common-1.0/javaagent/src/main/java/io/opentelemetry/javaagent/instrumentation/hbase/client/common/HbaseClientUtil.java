/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import java.lang.reflect.Method;
import javax.annotation.Nullable;

public class HbaseClientUtil {

  private static final ClassValue<Method> getNameMethod =
      new ClassValue<Method>() {
        @Nullable
        @Override
        protected Method computeValue(Class<?> type) {
          try {
            return type.getMethod("getName");
          } catch (NoSuchMethodException ignored) {
            return null;
          }
        }
      };

  @Nullable
  public static String methodDescriptorName(Object methodDescriptor) {
    try {
      Method method = getNameMethod.get(methodDescriptor.getClass());
      if (method != null) {
        return (String) method.invoke(methodDescriptor);
      }
    } catch (ReflectiveOperationException ignored) {
      // ignored
    }
    return null;
  }

  private HbaseClientUtil() {}
}
