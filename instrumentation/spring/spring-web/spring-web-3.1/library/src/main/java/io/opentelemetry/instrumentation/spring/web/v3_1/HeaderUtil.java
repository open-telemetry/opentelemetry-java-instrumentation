/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static java.util.Collections.emptyList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.http.HttpHeaders;

class HeaderUtil {
  @Nullable private static final MethodHandle GET_HEADERS;

  static {
    // since spring web 7.0
    MethodHandle methodHandle =
        findGetHeadersMethod(MethodType.methodType(List.class, String.class));
    if (methodHandle == null) {
      // up to spring web 7.0
      methodHandle = findGetHeadersMethod(MethodType.methodType(List.class, Object.class));
    }
    GET_HEADERS = methodHandle;
  }

  private static MethodHandle findGetHeadersMethod(MethodType methodType) {
    try {
      return MethodHandles.lookup().findVirtual(HttpHeaders.class, "get", methodType);
    } catch (Throwable t) {
      return null;
    }
  }

  @SuppressWarnings("unchecked") // casting MethodHandle.invoke result
  static List<String> getHeader(HttpHeaders headers, String name) {
    if (GET_HEADERS != null) {
      try {
        List<String> result = (List<String>) GET_HEADERS.invoke(headers, name);
        if (result == null) {
          return emptyList();
        }
        return result;
      } catch (Throwable t) {
        // ignore
      }
    }
    return emptyList();
  }

  private HeaderUtil() {}
}
