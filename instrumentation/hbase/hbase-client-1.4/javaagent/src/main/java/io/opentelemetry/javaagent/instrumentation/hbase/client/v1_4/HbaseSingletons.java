/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v1_4;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseRequest;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

public class HbaseSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.hbase-client-1.4";
  private static final Instrumenter<HbaseRequest, Void> instrumenter =
      HbaseInstrumenterFactory.create(INSTRUMENTATION_NAME);
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

  public static Instrumenter<HbaseRequest, Void> instrumenter() {
    return instrumenter;
  }

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

  private HbaseSingletons() {}
}
