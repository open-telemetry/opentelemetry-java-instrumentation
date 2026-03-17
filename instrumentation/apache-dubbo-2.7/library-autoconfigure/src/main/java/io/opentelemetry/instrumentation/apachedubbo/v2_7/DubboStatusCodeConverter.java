/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboStatusCodeUtil;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.annotation.Nullable;

final class DubboStatusCodeConverter {

  // MethodHandles for Triple status extraction (Dubbo 3.x only, loaded reflectively)
  @Nullable private static final Class<?> STATUS_RPC_EXCEPTION_CLASS;
  @Nullable private static final MethodHandle GET_STATUS;
  @Nullable private static final MethodHandle GET_CODE;

  static {
    Class<?> statusRpcExceptionClass = null;
    MethodHandle getStatus = null;
    MethodHandle getCode = null;

    try {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      statusRpcExceptionClass = Class.forName("org.apache.dubbo.rpc.StatusRpcException");
      Class<?> triRpcStatusClass = Class.forName("org.apache.dubbo.rpc.TriRpcStatus");

      getStatus =
          lookup.findVirtual(
              statusRpcExceptionClass, "getStatus", MethodType.methodType(triRpcStatusClass));

      Class<?> codeEnumClass = Class.forName("org.apache.dubbo.rpc.TriRpcStatus$Code");
      getCode = lookup.findGetter(triRpcStatusClass, "code", codeEnumClass);
    } catch (Throwable t) {
      // Triple classes not available (Dubbo 2.7.x runtime)
    }

    STATUS_RPC_EXCEPTION_CLASS = statusRpcExceptionClass;
    GET_STATUS = getStatus;
    GET_CODE = getCode;
  }

  static boolean isDubbo2ServerError(String statusCodeName) {
    return DubboStatusCodeUtil.isDubbo2ServerError(statusCodeName);
  }

  static boolean isTripleServerError(String statusCodeName) {
    return DubboStatusCodeUtil.isTripleServerError(statusCodeName);
  }

  /**
   * Extracts the Triple protocol status code name from a StatusRpcException. Returns null if the
   * exception is not a StatusRpcException or if Triple classes are not available.
   */
  @Nullable
  static String extractTripleStatusCode(@Nullable Throwable error) {
    if (error == null
        || STATUS_RPC_EXCEPTION_CLASS == null
        || GET_STATUS == null
        || GET_CODE == null) {
      return null;
    }
    if (!STATUS_RPC_EXCEPTION_CLASS.isInstance(error)) {
      return null;
    }
    try {
      Object triRpcStatus = GET_STATUS.invoke(error);
      if (triRpcStatus == null) {
        return null;
      }
      Object code = GET_CODE.invoke(triRpcStatus);
      return code != null ? code.toString() : null;
    } catch (Throwable t) {
      return null;
    }
  }

  private DubboStatusCodeConverter() {}
}
