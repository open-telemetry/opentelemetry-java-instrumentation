/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.api.internal.CauseUnwrapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionException;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

final class DubboRpcStatus {

  private static final String STATUS_RPC_EXCEPTION = "org.apache.dubbo.rpc.StatusRpcException";

  @Nullable private static final Method GET_STATUS = findMethod(STATUS_RPC_EXCEPTION, "getStatus");

  @Nullable
  private static final Field STATUS_CODE = findField("org.apache.dubbo.rpc.TriRpcStatus", "code");

  @Nullable
  static String getStatusCode(@Nullable Result response, @Nullable Throwable error) {
    String statusCode = getStatusCodeFromException(unwrap(error));
    if (statusCode == null && error == null && response != null) {
      statusCode = getStatusCodeFromException(unwrap(response.getException()));
    }
    return statusCode;
  }

  @Nullable
  static String getExceptionType(@Nullable Result response, @Nullable Throwable error) {
    Throwable exception = unwrap(error);
    if (exception == null && response != null) {
      exception = unwrap(response.getException());
    }
    return exception == null ? null : exception.getClass().getName();
  }

  static boolean isError(String statusCode, boolean isClient) {
    // Triple follows gRPC semantic conventions: all non-OK statuses are client errors,
    // but only the statuses listed below are server errors.
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/grpc.md#spans
    if (isClient) {
      return !"OK".equals(statusCode);
    }
    return "DATA_LOSS".equals(statusCode)
        || "DEADLINE_EXCEEDED".equals(statusCode)
        || "INTERNAL".equals(statusCode)
        || "UNAVAILABLE".equals(statusCode)
        || "UNIMPLEMENTED".equals(statusCode)
        || "UNKNOWN".equals(statusCode);
  }

  @Nullable
  private static Throwable unwrap(@Nullable Throwable error) {
    return error == null
        ? null
        : CauseUnwrapper.unwrap(error, e -> e instanceof CompletionException);
  }

  @Nullable
  private static String getStatusCodeFromException(@Nullable Throwable error) {
    if (error == null || GET_STATUS == null || !GET_STATUS.getDeclaringClass().isInstance(error)) {
      return null;
    }
    try {
      Object status = GET_STATUS.invoke(error);
      if (status == null || STATUS_CODE == null) {
        return null;
      }
      Object statusCode = STATUS_CODE.get(status);
      return statusCode instanceof Enum ? ((Enum<?>) statusCode).name() : null;
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  @Nullable
  private static Method findMethod(String className, String methodName) {
    try {
      Class<?> type = Class.forName(className, false, DubboRpcStatus.class.getClassLoader());
      return type.getMethod(methodName);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
      return null;
    }
  }

  @Nullable
  private static Field findField(String className, String fieldName) {
    try {
      Class<?> type = Class.forName(className, false, DubboRpcStatus.class.getClassLoader());
      return type.getField(fieldName);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
      return null;
    }
  }

  private DubboRpcStatus() {}
}
