/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState.getTableName;

import java.lang.reflect.Method;
import javax.annotation.Nullable;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos;

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
  private static String methodDescriptorName(Object methodDescriptor) {
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

  public static HbaseRequest createRequest(Object md, Object param, @Nullable String serverTarget) {
    String operation = methodDescriptorName(md);
    Long batchSize = null;
    if (param instanceof ClientProtos.MultiRequest) {
      HbaseBatchMetadata batchMetadata =
          HbaseBatchMetadata.create((ClientProtos.MultiRequest) param);
      operation = batchMetadata.getOperation();
      batchSize = batchMetadata.getOperationBatchSize();
    }

    return HbaseRequest.create(operation, getTableName(), serverTarget, batchSize);
  }

  private HbaseClientUtil() {}
}
