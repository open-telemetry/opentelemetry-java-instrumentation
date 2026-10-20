/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionException;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

final class DubboRpcStatus {

  private static final String STATUS_RPC_EXCEPTION = "org.apache.dubbo.rpc.StatusRpcException";

  private static final ClassValue<StatusRpcExceptionAccessor> STATUS_RPC_EXCEPTION_ACCESSORS =
      new ClassValue<StatusRpcExceptionAccessor>() {
        @Override
        protected StatusRpcExceptionAccessor computeValue(Class<?> type) {
          try {
            return new StatusRpcExceptionAccessor(type.getMethod("getStatus"));
          } catch (NoSuchMethodException e) {
            return new StatusRpcExceptionAccessor(null);
          }
        }
      };

  private static final ClassValue<StatusCodeAccessor> STATUS_CODE_ACCESSORS =
      new ClassValue<StatusCodeAccessor>() {
        @Override
        protected StatusCodeAccessor computeValue(Class<?> type) {
          try {
            return new StatusCodeAccessor(type.getField("code"));
          } catch (NoSuchFieldException e) {
            return new StatusCodeAccessor(null);
          }
        }
      };

  @Nullable
  static String getStatusCode(@Nullable Result response, @Nullable Throwable error) {
    String statusCode = getStatusCodeFromException(unwrap(error));
    if (statusCode == null && response != null) {
      statusCode = getStatusCodeFromException(unwrap(response.getException()));
    }
    return statusCode;
  }

  static boolean isError(String statusCode, boolean isClient) {
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
    while (error instanceof CompletionException && error.getCause() != null) {
      error = error.getCause();
    }
    return error;
  }

  @Nullable
  private static String getStatusCodeFromException(@Nullable Throwable error) {
    if (error == null || !STATUS_RPC_EXCEPTION.equals(error.getClass().getName())) {
      return null;
    }
    try {
      Method getStatus = STATUS_RPC_EXCEPTION_ACCESSORS.get(error.getClass()).getStatus;
      if (getStatus == null) {
        return null;
      }
      Object status = getStatus.invoke(error);
      if (status == null) {
        return null;
      }
      Field code = STATUS_CODE_ACCESSORS.get(status.getClass()).code;
      Object statusCode = code == null ? null : code.get(status);
      return statusCode instanceof Enum ? ((Enum<?>) statusCode).name() : null;
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  private static final class StatusRpcExceptionAccessor {

    @Nullable private final Method getStatus;

    private StatusRpcExceptionAccessor(@Nullable Method getStatus) {
      this.getStatus = getStatus;
    }
  }

  private static final class StatusCodeAccessor {

    @Nullable private final Field code;

    private StatusCodeAccessor(@Nullable Field code) {
      this.code = code;
    }
  }

  private DubboRpcStatus() {}
}
